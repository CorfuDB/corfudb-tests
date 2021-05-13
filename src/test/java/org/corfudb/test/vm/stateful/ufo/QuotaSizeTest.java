package org.corfudb.test.vm.stateful.ufo;

import com.sun.corba.se.impl.activation.CommandHandler;
import com.vmware.corfudb.universe.UniverseConfigurator;
import com.vmware.corfudb.universe.util.UfoUtils;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.runtime.collections.Query;
import org.corfudb.runtime.collections.Table;
import org.corfudb.runtime.collections.TxBuilder;
import org.corfudb.test.TestGroups;
import org.corfudb.test.TestSchema;
import org.corfudb.universe.UniverseManager;
import org.corfudb.universe.UniverseManager.UniverseWorkflow;
import org.corfudb.universe.group.cluster.CorfuCluster;
import org.corfudb.universe.group.cluster.vm.RemoteOperationHelper;
import org.corfudb.universe.node.client.CorfuClient;
import org.corfudb.universe.node.server.CorfuServer;
import org.corfudb.universe.node.server.vm.VmCorfuServer;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.log.TestLogHelper;
import org.corfudb.universe.universe.UniverseParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.Random;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static com.vmware.corfudb.universe.util.ScenarioUtils.*;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@Tag(TestGroups.STATEFUL)
public class QuotaSizeTest {

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
     *  - deploy a cluster: run com.vmware.corfudb.universe.management.Deployment
     *  - Shutdown the cluster com.vmware.corfudb.universe.management.Shutdown
     *
     *   Test cluster behavior after restart service on one node
     *       1)  Deploy and bootstrap a three nodes cluster, with corfu limit ".0001" %
     *           i.e. "Throttle disk usage to 69% of total disk capacity"
     *       2)  Create a table in corfu
     *       3)  Insert .1M records into the table
     *       4)  Verify: It should stop inserting the data ,by following exception
     *           " QuotaExceededException: Disk usage has exceeded the quota set, system is now in read-only mode. "
     *
     */
    @Test
    public void test() {

        universeManager.workflow(wf -> {
            wf.setupVm(configurator.vmSetup);
            wf.setupVm(fixture -> {
                //don't stop corfu cluster after the test
                fixture.getServers().logSizeQuotaPercentage(.0001);
                fixture.getUniverse().cleanUpEnabled(false);
            });
            wf.initUniverse();
            try {
                verifyTest(wf);
            } catch (InterruptedException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                fail("Failed", e);
            }
        });
    }

    private void verifyTest(UniverseWorkflow<Fixture<UniverseParams>> wf)
            throws InterruptedException, NoSuchMethodException,
            IllegalAccessException, InvocationTargetException {

        count = 100000;

        UniverseParams params = wf.getFixture().data();
        CorfuCluster corfuCluster = wf.getUniverse()
                .getGroup(params.getGroupParamByIndex(0).getName());
        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        //Check CLUSTER STATUS
        log.info("**** before running testcase, verify cluster status ****");
        waitForClusterStatusStable(corfuClient);
        log.info("*** cluster status is STABLE, executing testcase ***");

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore = new CorfuStore(runtime);

        // Define a namespace for the table.
        String nsxManager = "nsx-manager";
        // Define table name
        String tableName = "QuotaSizeTable";

        // Create & Register the table.
        // This is required to initialize the table for the current corfu client.
        Table<TestSchema.IdMessage, TestSchema.EventInfo, TestSchema.ManagedResources> table =
                UfoUtils.createTable(corfuStore, nsxManager, tableName);

        List<TestSchema.IdMessage> uuids = new ArrayList<>();
        List<TestSchema.EventInfo> events = new ArrayList<>();
        TestSchema.ManagedResources metadata = TestSchema.ManagedResources.newBuilder()
                .setCreateUser("MrProto")
                .build();
        // Creating a transaction builder.
        TxBuilder tx = corfuStore.tx(nsxManager);

        VmCorfuServer vm = (VmCorfuServer) corfuCluster.getServerByIndex(2);
        RemoteOperationHelper commandHelper = vm.getRemoteOperationHelper();

        // Data insertion & Verification
        UfoUtils.generateDataAndCommit(0, count, tableName, uuids, events, tx, metadata, false);

        /* TODO: check wheather corfu raise the follwing exception or not:
                 QuotaExceededException: Disk usage has exceeded the quota set, system is now in read-only mode."
                 and verify
         */

        log.info("**** Clear the Table ****");
        Query q = corfuStore.query(nsxManager);
        UfoUtils.clearTableAndVerify(table, tableName, q);

    }
}
