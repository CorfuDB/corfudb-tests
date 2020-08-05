package org.corfudb.universe.test.util;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.runtime.collections.Query;
import org.corfudb.runtime.collections.Table;
import org.corfudb.runtime.collections.TableOptions;
import org.corfudb.runtime.collections.TxBuilder;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.TestSchema.IdMessage;
import org.corfudb.test.TestSchema.ManagedResources;
import org.corfudb.universe.test.UniverseConfigurator;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public final class UfoUtils {


    private UfoUtils() {
        //prevent creating instances
    }

    enum EventType {
        EVENT, UPDATE
    }

    /**
     * Create & Register the table.
     * This is required to initialize the table for the current corfu client.
     *
     * @param corfuStore corfu CorfuStore.
     * @param namespace  corfu namespace
     * @param tableName  corfu table-name
     */
    public static Table<IdMessage, EventInfo, ManagedResources> createTable(
            CorfuStore corfuStore, String namespace, String tableName)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {

        return corfuStore.openTable(
                namespace,
                tableName,
                IdMessage.class,
                EventInfo.class,
                ManagedResources.class,
                // TableOptions includes option to choose - Memory/Disk based corfu table.
                TableOptions.builder().build());
    }

    public static void generateDataAndCommit(
            int start, int end, String
            tableName, TxBuilder tx, ManagedResources metadata, boolean isUpdate){

        generateDataAndCommit(
                start, end, tableName, new ArrayList<>(), new ArrayList<>(), tx, metadata, isUpdate
        );
    }

    /**
     * Generate events and update a table
     *
     * @param start     start
     * @param end       end
     * @param tableName table name
     * @param uuids     list of ids
     * @param events    list of events
     * @param tx        transaction builder
     * @param metadata  metadata info
     * @param isUpdate  whether to update the table or not
     */
    public static void generateDataAndCommit(
            int start, int end, String tableName, List<IdMessage> uuids,
            List<EventInfo> events, TxBuilder tx, ManagedResources metadata, boolean isUpdate) {


        String eventName = EventType.EVENT.name();
        for (int i = start; i < end; i++) {
            byte[] bytes = Integer.toString(i).getBytes(StandardCharsets.UTF_8);
            UUID uuid = UUID.nameUUIDFromBytes(bytes);
            IdMessage uuidMsg = IdMessage.newBuilder()
                    .setMsb(uuid.getMostSignificantBits())
                    .setLsb(uuid.getLeastSignificantBits())
                    .build();

            if (isUpdate) {
                uuids.set(i, uuidMsg);

                eventName = EventType.UPDATE.name();
                events.set(i, EventInfo.newBuilder()
                        .setId(i)
                        .setName(eventName + i)
                        .setEventTime(i)
                        .build());
            } else {
                uuids.add(uuidMsg);
                events.add(EventInfo.newBuilder()
                        .setId(i)
                        .setName(eventName + i)
                        .setEventTime(i)
                        .build());
            }

            tx.update(tableName, uuids.get(i), events.get(i), metadata);
        }

        tx.commit();
    }

    /**
     * Verifies that a table has expected count of rows
     *
     * @param corfuStore       store
     * @param namespace        namespace
     * @param tableName        table name
     * @param expectedRowCount expected number of rows
     */
    public static void verifyTableRowCount(
            CorfuStore corfuStore, String namespace, String tableName, int expectedRowCount) {

        final Query q = corfuStore.query(namespace);

        log.info(" verify table using row count ");
        assertThat(q.count(tableName)).isEqualTo(expectedRowCount);
    }

    /**
     * Verify corfu table
     *
     * @param corfuStore     store
     * @param start          start
     * @param end            end
     * @param namespace      namespace
     * @param tableName      table name
     * @param updatedContent updated content
     */
    public static void verifyTableData(
            CorfuStore corfuStore, int start, int end, String namespace, String tableName,
            boolean updatedContent) {

        final Query q = corfuStore.query(namespace);
        String eventName = EventType.EVENT.name();
        if (updatedContent) {
            eventName = EventType.UPDATE.name();
        }
        for (int key = start; key < end; key++) {
            byte[] bytes = Integer.toString(key).getBytes(StandardCharsets.UTF_8);
            UUID uuid = UUID.nameUUIDFromBytes(bytes);
            IdMessage keyValue1 = IdMessage.newBuilder()
                    .setMsb(uuid.getMostSignificantBits())
                    .setLsb(uuid.getLeastSignificantBits())
                    .build();

            EventInfo expectedValue = EventInfo.newBuilder()
                    .setId(key)
                    .setName(eventName + key)
                    .setEventTime(key)
                    .build();

            assertThat(q.getRecord(tableName, keyValue1).getPayload()).isEqualTo(expectedValue);
        }
    }

    /**
     * Clear corfu table and verify that it is emty
     *
     * @param table     ufo table
     * @param tableName table name
     * @param queryObj  query object
     */
    public static void clearTableAndVerify(
            Table<IdMessage, EventInfo, ManagedResources> table, String tableName, Query queryObj) {

        //Delete all the table entries
        log.info("Clearing all the entries present in table");
        table.clear();

        log.info("Verify table entries are cleared");
        assertThat(queryObj.count(tableName)).isEqualTo(0);
    }

    /**
     * Whether cleaning of test data enabled or not
     *
     * @return if clean up is enabled
     */
    public static boolean cleanTestDataEnabled() {
        // Returns value for "test.data.clean" key from universe-tests.properties
        Properties props = UniverseConfigurator.getCfg();
        return Boolean.parseBoolean(props.getProperty("test.data.clean"));
    }
}
