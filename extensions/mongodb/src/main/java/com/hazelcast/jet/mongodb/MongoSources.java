/*
 * Copyright 2023 Hazelcast Inc.
 *
 * Licensed under the Hazelcast Community License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://hazelcast.com/hazelcast-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet.mongodb;

import com.hazelcast.function.SupplierEx;
import com.hazelcast.jet.mongodb.MongoSourceBuilder.Batch;
import com.hazelcast.jet.mongodb.MongoSourceBuilder.Stream;
import com.hazelcast.jet.pipeline.BatchSource;
import com.hazelcast.jet.pipeline.DataLinkRef;
import com.hazelcast.jet.pipeline.StreamSource;
import com.hazelcast.spi.annotation.Beta;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.bson.conversions.Bson;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.hazelcast.jet.mongodb.impl.MongoUtilities.bsonTimestampFromTimeMillis;

/**
 * Contains factory methods for MongoDB sources.
 * <p>
 * See {@link MongoSourceBuilder} for creating custom MongoDB sources.
 *
 * @since 5.3
 */
public final class MongoSources {

    private MongoSources() {
    }

    /**
     * Creates as builder for new batch mongo source. Equivalent to calling {@link MongoSourceBuilder#batch}.
     * <p>
     * Example usage:
     * <pre>{@code
     * BatchSource<Document> batchSource =
     *         MongoSources.batch("batch-source", () -> MongoClients.create("mongodb://127.0.0.1:27017"))
     *                 .into("myDatabase", "myCollection")
     *                 .filter(new Document("age", new Document("$gt", 10)),
     *                 .projection(new Document("age", 1))
     *         );
     * Pipeline p = Pipeline.create();
     * BatchStage<Document> srcStage = p.readFrom(batchSource);
     * }</pre>
     *
     * @since 5.3
     * @param name descriptive name for the source (diagnostic purposes) client.
     * @param clientSupplier a function that creates MongoDB client.
     * @return Batch Mongo source builder
     */
    @Beta
    @Nonnull
    public static MongoSourceBuilder.Batch<Document> batch(
            @Nonnull String name,
            @Nonnull SupplierEx<? extends MongoClient> clientSupplier) {
        return MongoSourceBuilder.batch(name, clientSupplier);
    }

    /**
     * Creates as builder for new batch mongo source. Equivalent to calling {@link MongoSourceBuilder#batch}.
     * <p>
     * Example usage:
     * <pre>{@code
     * BatchSource<Document> batchSource =
     *         MongoSources.batch("batch-source", dataLinkRef("mongo"))
     *                 .into("myDatabase", "myCollection")
     *                 .filter(new Document("age", new Document("$gt", 10)),
     *                 .projection(new Document("age", 1))
     *         );
     * Pipeline p = Pipeline.create();
     * BatchStage<Document> srcStage = p.readFrom(batchSource);
     * }</pre>
     *
     * Connector will use provided data link reference to obtain an instance of {@link MongoClient}. Depending
     * on the configuration this client may be shared between processors or not.
     *
     * @since 5.3
     * @param name descriptive name for the source (diagnostic purposes) client.
     * @param dataLinkRef a reference to mongo data link
     * @return Batch Mongo source builder
     */
    @Beta
    @Nonnull
    public static MongoSourceBuilder.Batch<Document> batch(
            @Nonnull String name,
            @Nonnull DataLinkRef dataLinkRef) {
        return MongoSourceBuilder.batch(name, dataLinkRef);
    }

    /**
     * Returns a MongoDB batch source which queries the collection using given
     * {@code filter} and applies the given {@code projection} on the documents.
     * <p>
     * See {@link MongoSourceBuilder} for creating custom MongoDB sources.
     * <p>
     * Here's an example which queries documents in a collection having the
     * field {@code age} with a value greater than {@code 10} and applies a
     * projection so that only the {@code age} field is returned in the
     * emitted document.
     *
     * <pre>{@code
     * BatchSource<Document> batchSource =
     *         MongoSources.batch(
     *                 "batch-source",
     *                 "mongodb://127.0.0.1:27017",
     *                 "myDatabase",
     *                 "myCollection",
     *                 new Document("age", new Document("$gt", 10)),
     *                 new Document("age", 1)
     *         );
     * Pipeline p = Pipeline.create();
     * BatchStage<Document> srcStage = p.readFrom(batchSource);
     * }</pre>
     *
     * @since 5.3
     *
     * @param name             a descriptive name for the source (diagnostic purposes)
     * @param connectionString a connection string URI to MongoDB for example:
     *                         {@code mongodb://127.0.0.1:27017}
     * @param database         the name of the database
     * @param collection       the name of the collection
     * @param filter           filter object as a {@link Document}
     * @param projection       projection object as a {@link Document}
     */
    @Beta
    @Nonnull
    public static BatchSource<Document> batch(
            @Nonnull String name,
            @Nonnull String connectionString,
            @Nonnull String database,
            @Nonnull String collection,
            @Nullable Bson filter,
            @Nullable Bson projection
    ) {
        Batch<Document> builder = MongoSourceBuilder
                .batch(name, () -> MongoClients.create(connectionString))
                .database(database)
                .collection(collection);
        if (projection != null) {
            builder.project(projection);
        }
        if (filter != null) {
            builder.filter(filter);
        }
        return builder.build();
    }

    /**
     * Returns a MongoDB batch source which queries the collection using given
     * {@code filter} and applies the given {@code projection} on the documents.
     * <p>
     * See {@link MongoSourceBuilder} for creating custom MongoDB sources.
     * <p>
     * Here's an example which queries documents in a collection having the
     * field {@code age} with a value greater than {@code 10} and applies a
     * projection so that only the {@code age} field is returned in the
     * emitted document.
     *
     * <pre>{@code
     * BatchSource<Document> batchSource =
     *         MongoSources.batch(
     *                 "batch-source",
     *                 dataLinkRef("mongoDb"),
     *                 "myDatabase",
     *                 "myCollection",
     *                 new Document("age", new Document("$gt", 10)),
     *                 new Document("age", 1)
     *         );
     * Pipeline p = Pipeline.create();
     * BatchStage<Document> srcStage = p.readFrom(batchSource);
     * }</pre>
     *
     * Connector will use provided data link reference to obtain an instance of {@link MongoClient}. Depending
     * on the configuration this client may be shared between processors or not.
     *
     * @since 5.3
     *
     * @param name             a descriptive name for the source (diagnostic purposes)
     * @param dataLinkRef      a reference to some mongo data link
     * @param database         the name of the database
     * @param collection       the name of the collection
     * @param filter           filter object as a {@link Document}
     * @param projection       projection object as a {@link Document}
     */
    @Beta
    @Nonnull
    public static BatchSource<Document> batch(
            @Nonnull String name,
            @Nonnull DataLinkRef dataLinkRef,
            @Nonnull String database,
            @Nonnull String collection,
            @Nullable Bson filter,
            @Nullable Bson projection
    ) {
        Batch<Document> builder = MongoSourceBuilder
                .batch(name, dataLinkRef)
                .database(database)
                .collection(collection);
        if (projection != null) {
            builder.project(projection);
        }
        if (filter != null) {
            builder.filter(filter);
        }
        return builder.build();
    }

    /**
     * Creates as builder for new stream mongo source. Equivalent to calling {@link MongoSourceBuilder#stream}.
     *
     * Example usage:
     * <pre>{@code
     * StreamSource<Document> streamSource =
     *         MongoSources.stream("batch-source", () -> MongoClients.create("mongodb://127.0.0.1:27017"))
     *                 .into("myDatabase", "myCollection")
     *                 .filter(new Document("fullDocument.age", new Document("$gt", 10)),
     *                 .projection(new Document("fullDocument.age", 1))
     *         );
     * Pipeline p = Pipeline.create();
     * StreamStage<Document> srcStage = p.readFrom(streamSource);
     * }</pre>
     *
     * @since 5.3
     *
     * @param name descriptive name for the source (diagnostic purposes) client.
     * @param clientSupplier a function that creates MongoDB client.
     * @return Stream Mongo source builder
     */
    @Beta
    @Nonnull
    public static MongoSourceBuilder.Stream<Document> stream(
            @Nonnull String name,
            @Nonnull SupplierEx<? extends MongoClient> clientSupplier) {
        return MongoSourceBuilder.stream(name, clientSupplier);
    }

    /**
     * Returns a MongoDB stream source which watches the changes on the
     * collection. The source applies the given {@code filter} and {@code
     * projection} on the change stream documents.
     * <p>
     * Change stream is available for replica sets and sharded clusters that
     * use WiredTiger storage engine and replica set protocol version 1 (pv1).
     * Change streams can also be used on deployments which employ MongoDB's
     * encryption-at-rest feature. You cannot watch on system collections and
     * collections in admin, local and config databases.
     * <p>
     * See {@link MongoSourceBuilder} for creating custom MongoDB sources.
     * <p>
     * Here's an example which streams inserts on a collection having the
     * field {@code age} with a value greater than {@code 10} and applies a
     * projection so that only the {@code age} field is returned in the
     * emitted document.
     *
     * <pre>{@code
     * StreamSource<? extends Document> streamSource =
     *         MongoSources.stream(
     *                 "stream-source",
     *                 "mongodb://127.0.0.1:27017",
     *                 "myDatabase",
     *                 "myCollection",
     *                 new Document("fullDocument.age", new Document("$gt", 10))
     *                         .append("operationType", "insert"),
     *                 new Document("fullDocument.age", 1)
     *         );
     *
     * Pipeline p = Pipeline.create();
     * StreamSourceStage<? extends Document> srcStage = p.readFrom(streamSource);
     * }</pre>
     *
     * @since 5.3
     *
     * @param name             a descriptive name for the source (diagnostic purposes)
     * @param connectionString a connection string URI to MongoDB for example:
     *                         {@code mongodb://127.0.0.1:27017}
     * @param database         the name of the database
     * @param collection       the name of the collection
     * @param filter           filter object as a {@link Document}
     * @param projection       projection object as a {@link Document}
     */
    @Beta
    @Nonnull
    public static StreamSource<? extends Document> stream(
            @Nonnull String name,
            @Nonnull String connectionString,
            @Nonnull String database,
            @Nonnull String collection,
            @Nullable Document filter,
            @Nullable Document projection
    ) {
        Stream<Document> builder = MongoSourceBuilder
                .stream(name, () -> MongoClients.create(connectionString))
                .database(database)
                .collection(collection)
                .mapFn((d, t) -> d.getFullDocument());
        if (projection != null) {
            builder.project(projection);
        }
        if (filter != null) {
            builder.filter(filter);
        }
        builder.startAtOperationTime(bsonTimestampFromTimeMillis(System.currentTimeMillis()));
        return builder.build();
    }
}
