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

package com.hazelcast.internal.tpcengine.nio;

import com.hazelcast.internal.tpcengine.AsyncSocket;
import com.hazelcast.internal.tpcengine.AsyncSocketBuilder;
import com.hazelcast.internal.tpcengine.Option;
import com.hazelcast.internal.tpcengine.ReadHandler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;

import static com.hazelcast.internal.tpcengine.util.ExceptionUtil.sneakyThrow;
import static com.hazelcast.internal.tpcengine.util.Preconditions.checkNotNull;
import static com.hazelcast.internal.tpcengine.util.Preconditions.checkPositive;

/**
 * A {@link AsyncSocketBuilder} specific to the {@link NioAsyncSocket}.
 */
public class NioAsyncSocketBuilder implements AsyncSocketBuilder {
    static final int DEFAULT_WRITE_QUEUE_CAPACITY = 2 << 16;

    final NioReactor reactor;
    final SocketChannel socketChannel;
    final NioAcceptRequest acceptRequest;
    final boolean clientSide;
    boolean regularSchedule = true;
    boolean writeThrough;
    boolean receiveBufferIsDirect = true;
    int writeQueueCapacity = DEFAULT_WRITE_QUEUE_CAPACITY;
    ReadHandler readHandler;
    NioAsyncSocketOptions options;
    private boolean build;

    NioAsyncSocketBuilder(NioReactor reactor, NioAcceptRequest acceptRequest) {
        try {
            this.reactor = reactor;
            this.acceptRequest = acceptRequest;
            if (acceptRequest == null) {
                this.socketChannel = SocketChannel.open();
                this.clientSide = true;
            } else {
                this.socketChannel = acceptRequest.socketChannel;
                this.clientSide = false;
            }
            this.socketChannel.configureBlocking(false);
            this.options = new NioAsyncSocketOptions(socketChannel);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public <T> boolean setIfSupported(Option<T> option, T value) {
        verifyNotBuild();

        return options.setIfSupported(option, value);
    }

    public NioAsyncSocketBuilder setReceiveBufferIsDirect(boolean receiveBufferIsDirect) {
        verifyNotBuild();

        this.receiveBufferIsDirect = receiveBufferIsDirect;
        return this;
    }

    public NioAsyncSocketBuilder setWriteQueueCapacity(int writeQueueCapacity) {
        verifyNotBuild();

        this.writeQueueCapacity = checkPositive(writeQueueCapacity, "writeQueueCapacity");
        return this;
    }

    public NioAsyncSocketBuilder setRegularSchedule(boolean regularSchedule) {
        verifyNotBuild();

        this.regularSchedule = regularSchedule;
        return this;
    }

    public NioAsyncSocketBuilder setWriteThrough(boolean writeThrough) {
        verifyNotBuild();

        this.writeThrough = writeThrough;
        return this;
    }

    /**
     * Sets the read handler. Should be called before this AsyncSocket is started.
     *
     * @param readHandler the ReadHandler
     * @return this
     * @throws NullPointerException if readHandler is null.
     */
    public final NioAsyncSocketBuilder setReadHandler(ReadHandler readHandler) {
        verifyNotBuild();

        this.readHandler = checkNotNull(readHandler);
        return this;
    }

    @SuppressWarnings("java:S1181")
    @Override
    public AsyncSocket build() {
        verifyNotBuild();

        build = true;

        if (readHandler == null) {
            throw new IllegalStateException("readHandler is not configured.");
        }

        if (Thread.currentThread() == reactor.eventloopThread()) {
            return new NioAsyncSocket(NioAsyncSocketBuilder.this);
        } else {
            CompletableFuture<NioAsyncSocket> future = new CompletableFuture<>();
            reactor.execute(() -> {
                try {
                    NioAsyncSocket asyncSocket = new NioAsyncSocket(NioAsyncSocketBuilder.this);
                    future.complete(asyncSocket);
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                    throw sneakyThrow(e);
                }
            });

            return future.join();
        }
    }

    private void verifyNotBuild() {
        if (build) {
            throw new IllegalStateException("Can't call build twice on the same AsyncSocketBuilder");
        }
    }
}
