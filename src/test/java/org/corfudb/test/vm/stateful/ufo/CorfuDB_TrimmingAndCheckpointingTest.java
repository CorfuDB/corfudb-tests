package org.corfudb.test.vm.stateful.ufo;

import com.google.common.reflect.TypeToken;
import com.vmware.corfudb.universe.UniverseConfigurator;
import com.vmware.corfudb.universe.util.UfoUtils;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.protocols.wireprotocol.Token;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.MultiCheckpointWriter;
import org.corfudb.runtime.collections.*;
import org.corfudb.runtime.view.ObjectOpenOption;
import org.corfudb.runtime.view.TableRegistry;
import org.corfudb.test.TestGroups;
import org.corfudb.test.TestSchema;
import org.corfudb.universe.UniverseManager;
import org.corfudb.universe.group.cluster.CorfuCluster;
import org.corfudb.universe.node.client.CorfuClient;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.log.TestLogHelper;
import org.corfudb.universe.universe.UniverseParams;
import org.corfudb.util.serializer.DynamicProtobufSerializer;
import org.corfudb.util.serializer.ISerializer;
import org.corfudb.util.serializer.Serializers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static com.vmware.corfudb.universe.util.ScenarioUtils.waitForClusterStatusStable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@Slf4j
@Tag(TestGroups.STATEFUL)
public class CorfuDB_TrimmingAndCheckpointingTest {

    @BeforeEach
    public void testSetUp() {
        TestLogHelper.startTestLogging(getClass());
    }

    @AfterEach
    public void testCleanUp() {
        TestLogHelper.stopTestLogging();
    }

    private final UniverseConfigurator configurator = UniverseConfigurator.builder().build();
    private final UniverseManager universeManager = configurator.universeManager;

    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run com.vmware.corfudb.universe.management.Deployment
     * - Shutdown the cluster com.vmware.corfudb.universe.management.Shutdown
     * <p>
     * Test trimming and checkpointer i.e. garbage collection
     * 1) Deploy and bootstrap a three nodes cluster
     * 2) Verify Cluster is stable after deployment
     * 3) Add 100 records into table and verify all records one by one
     * 4) Update records from index 60 to 90 and verify updated records
     * 5) Remove all records from table
     * 6) Create dynamic corfu table and registry
     * 7) Check corfu table size
     * 8) Add corfu table and registry to Multi checkpointer map
     * 9) Trim data
     */

    @Test
    public void testTrimming() {

        universeManager.workflow(wf -> {
            wf.setupVm(configurator.vmSetup);
            wf.setupVm(fixture -> {
                //don't stop corfu cluster after the test
                fixture.getUniverse().cleanUpEnabled(false);
            });
            wf.deploy();
            try {
                verifyTrimmingAndCheckpointing(wf);
            } catch (Exception e) {
                fail("Failed: ", e);
            }
        });
    }

    /**
     * Basic test that inserts a single item using protobuf defined in the proto/ directory.
     */
    private void verifyTrimmingAndCheckpointing(UniverseManager.UniverseWorkflow<Fixture<UniverseParams>> wf) throws InterruptedException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        CorfuCluster corfuCluster1 = wf.getUniverse()
                .getGroup(wf.getFixture().data().getGroupParamByIndex(0).getName());

        CorfuClient corfuClient1 = corfuCluster1.getLocalCorfuClient();
        CorfuRuntime runtime1 = corfuClient1.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore1 = new CorfuStore(runtime1);

        log.info("**** Verify cluster status is stable ****");
        waitForClusterStatusStable(corfuClient1);

        // Define a namespace for the table.
        String nsxNamespace = "nsx-manager";
        // Define table name
        String tableName = "CorfuDB_TrimmingAndCheckpointingTable";

        Table<TestSchema.IdMessage, TestSchema.EventInfo, TestSchema.ManagedResources> table =
                UfoUtils.createTable(corfuStore1, nsxNamespace, tableName);

        final int count = 100;
        List<TestSchema.IdMessage> uuids = new ArrayList<>();
        List<TestSchema.EventInfo> events = new ArrayList<>();
        TestSchema.ManagedResources metadata = TestSchema.ManagedResources.newBuilder()
                .setCreateUser("MrProto")
                .build();
        // Creating a transaction builder.
        TxBuilder tx = corfuStore1.tx(nsxNamespace);

        log.info("**** Adding data into table ****");
        UfoUtils.generateDataAndCommit(0, count, tableName, uuids, events, tx, metadata, false);
        log.info("**** Verify table row count ****");
        UfoUtils.verifyTableRowCount(corfuStore1, nsxNamespace, tableName, count);
        log.info("**** Verify Table Data one by one ****");
        UfoUtils.verifyTableData(corfuStore1, 0, count, nsxNamespace, tableName, false);
        log.info("**** Update the records ****");
        UfoUtils.generateDataAndCommit(60, 90, tableName, uuids, events, tx, metadata, true);
        log.info("**** Verify the updated data ****");
        UfoUtils.verifyTableData(corfuStore1, 60, 90, nsxNamespace, tableName, true);

        // Creating separate runtime for dynamic corfutable
        CorfuRuntime runtime = corfuClient1.getRuntime();

        ISerializer dynamicProtobufSerializer = new DynamicProtobufSerializer(runtime);
        Serializers.registerSerializer(dynamicProtobufSerializer);

        log.info("**** Open corfu dynamic table ****");
        CorfuTable<CorfuDynamicKey, CorfuDynamicRecord> corfuTable = runtime.getObjectsView().build()
                .setTypeToken(new TypeToken<CorfuTable<CorfuDynamicKey, CorfuDynamicRecord>>() {
                })
                .setStreamName(TableRegistry.getFullyQualifiedTableName(nsxNamespace, tableName))
                .setSerializer(dynamicProtobufSerializer)
                .open();

        assertThat(corfuTable.size()).isEqualTo(100);

        MultiCheckpointWriter<CorfuTable> mcw = new MultiCheckpointWriter<>();

        log.info("**** Create table registry ****");
        CorfuTable<CorfuDynamicKey, CorfuDynamicRecord> tableRegistry = runtime.getObjectsView().build()
                .setTypeToken(new TypeToken<CorfuTable<CorfuDynamicKey, CorfuDynamicRecord>>() {
                })
                .setStreamName(TableRegistry.getFullyQualifiedTableName(TableRegistry.CORFU_SYSTEM_NAMESPACE,
                        TableRegistry.REGISTRY_TABLE_NAME))
                .setSerializer(dynamicProtobufSerializer)
                .addOpenOption(ObjectOpenOption.NO_CACHE)
                .open();

        log.info("**** Appending checkpoint ****");
        mcw.addMap(corfuTable);
        mcw.addMap(tableRegistry);

        Token trimPoint = mcw.appendCheckpoints(runtime, "checkpointer");

        log.info("**** Trimming data ****");
        runtime.getAddressSpaceView().prefixTrim(trimPoint);
        runtime.getAddressSpaceView().gc();

        log.info("**** Verify checkpointer count ****");
        log.info(String.format("%s", runtime.getAddressSpaceView().getTrimMark().getSequence()));
        log.info(String.format("%s", runtime.getAddressSpaceView().getAllTails().getLogTail()));
        log.info(String.format("%s", runtime.getAddressSpaceView().getAllTails().getStreamTails()));


        Query q = corfuStore1.query(nsxNamespace);
        log.info("**** Clear table data ****");
        UfoUtils.clearTableAndVerify(table, tableName, q);
    }
}
