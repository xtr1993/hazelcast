/*
 * Copyright (c) 2008-2023, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.client.impl.connection.tcp;

import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.cluster.Address;
import com.hazelcast.core.HazelcastException;
import com.hazelcast.internal.networking.Channel;
import com.hazelcast.internal.networking.ChannelInitializer;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.LoggingService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hazelcast.client.impl.protocol.ClientMessage.UNFRAGMENTED_MESSAGE;
import static com.hazelcast.client.impl.protocol.codec.builtin.FixedSizeTypesCodec.UUID_SIZE_IN_BYTES;
import static com.hazelcast.client.impl.protocol.codec.builtin.FixedSizeTypesCodec.encodeUUID;
import static com.hazelcast.internal.nio.IOUtil.closeResource;

/**
 * Establishes channels to the TPC ports of a connection in a
 * non-blocking way.
 * <p>
 * Upon failures, closes all channels established so far, along
 * with the connection.
 */
public final class TpcChannelConnector {
    private final UUID clientUuid;
    private final TcpClientConnection connection;
    private final List<Integer> tpcPorts;
    private final ExecutorService executor;
    private final ChannelInitializer channelInitializer;
    private final ChannelCreator channelCreator;
    private final ILogger logger;
    private final Channel[] tpcChannels;
    private final AtomicInteger remaining;
    private volatile boolean failed;

    public TpcChannelConnector(UUID clientUuid,
                               TcpClientConnection connection,
                               List<Integer> tpcPorts,
                               ExecutorService executor,
                               ChannelInitializer channelInitializer,
                               ChannelCreator channelCreator,
                               LoggingService loggingService) {
        this.clientUuid = clientUuid;
        this.connection = connection;
        this.tpcPorts = tpcPorts;
        this.executor = executor;
        this.channelInitializer = channelInitializer;
        this.channelCreator = channelCreator;
        this.logger = loggingService.getLogger(TpcChannelConnector.class);
        this.tpcChannels = new Channel[tpcPorts.size()];
        this.remaining = new AtomicInteger(tpcPorts.size());
    }

    /**
     * Initiates the connection attempts.
     * <p>
     * This call does not block.
     */
    public void initiate() {
        logger.info("Initiating connection attempts to TPC channels running on ports "
                + tpcPorts + " for " + connection);
        String host = connection.getRemoteAddress().getHost();
        int i = 0;
        for (int port : tpcPorts) {
            int index = i++;
            executor.submit(() -> connect(host, port, index));
        }
    }

    private void connect(String host, int port, int index) {
        if (connectionFailed()) {
            // No need to try to connect if one of the channels
            // or the connection itself is closed/failed.
            logger.warning("The connection to TPC channel on port " + port + " for "
                    + connection + " will not be made as either the connection or "
                    + "one of the TPC channel connections has failed.");
            return;
        }

        logger.info("Trying to connect to TPC channel on port " + port + " for " + connection);

        Channel channel = null;
        try {
            Address address = new Address(host, port);
            channel = channelCreator.create(address, connection, channelInitializer);
            writeAuthenticationBytes(channel);
            onSuccessfulChannelConnection(channel, index);
        } catch (Exception e) {
            logger.warning("Exception during the connection to attempt to TPC channel on port "
                    + port + " for " + connection + ": " + e, e);
            onFailure(channel);
        }
    }

    private void writeAuthenticationBytes(Channel channel) {
        // first thing we need to send is the clientUuid so this new socket can be connected
        // to the connection on the member.
        ClientMessage clientUuidMessage = ClientMessage.createForEncode();
        ClientMessage.Frame initialFrame = new ClientMessage.Frame(
                new byte[UUID_SIZE_IN_BYTES], UNFRAGMENTED_MESSAGE);
        encodeUUID(initialFrame.content, 0, clientUuid);
        clientUuidMessage.add(initialFrame);
        if (!channel.write(clientUuidMessage)) {
            throw new HazelcastException("Cannot write authentication bytes to the TPC channel "
                    + channel + " for " + connection);
        }
    }

    private void onSuccessfulChannelConnection(Channel channel, int index) {
        synchronized (tpcChannels) {
            if (connectionFailed()) {
                // It might be the case that the connection or any
                // of the channels are failed after this channel
                // is established. We need to close this one as well
                // to not leak any channels.
                logger.warning("Closing the TPC channel " + channel + " for " + connection
                        + " as one of the connections is failed.");
                onFailure(channel);
                return;
            }

            tpcChannels[index] = channel;
        }

        logger.info("Successfully connected to TPC channel " + channel + " for " + connection);

        if (remaining.decrementAndGet() == 0) {
            connection.setTpcChannels(tpcChannels);

            // If the connection is alive at this point, but
            // closes afterward, the channels will be cleaned up
            // properly in the connection's close method, because
            // we have already written the channels.

            // If the connection is not alive at this point, the channels
            // might or might not be closed, depending on the order of the
            // close and setTpcChannels calls. We will close channels
            // if the connection is not alive here, just in case, as it is
            // OK to call close on already closed channels.
            if (!connection.isAlive()) {
                logger.warning("Closing all TPC channel connections for "
                        + connection + " as the connection is closed.");
                closeAllChannels();
            } else {
                logger.info("All TPC channel connections are established for the " + connection);
            }
        }
    }

    private void onFailure(Channel channel) {
        synchronized (tpcChannels) {
            closeChannel(channel);
            if (failed) {
                return;
            }

            failed = true;
            closeAllChannels();
            logger.warning("TPC channel establishments for the " + connection + " have failed. "
                    + "The client will not be using the TPC channels to route partition specific invocations, "
                    + "and fallback to the smart routing mode for this connection. Check the firewall settings "
                    + "to make sure the TPC channels are accessible from the client.");
        }
    }

    private boolean connectionFailed() {
        return failed || !connection.isAlive();
    }

    private void closeChannel(Channel channel) {
        closeResource(channel);
    }

    private void closeAllChannels() {
        for (Channel channel : tpcChannels) {
            closeChannel(channel);
        }
    }

    /**
     * Creates a Channel with the given parameters.
     */
    @FunctionalInterface
    public interface ChannelCreator {
        Channel create(Address address, TcpClientConnection connection, ChannelInitializer channelInitializer);
    }
}
