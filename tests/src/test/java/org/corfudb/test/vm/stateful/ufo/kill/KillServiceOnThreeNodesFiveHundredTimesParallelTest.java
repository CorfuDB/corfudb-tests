package org.corfudb.test.vm.stateful.ufo.kill;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.runtime.collections.Query;
import org.corfudb.runtime.collections.Table;
import org.corfudb.runtime.collections.TxBuilder;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.test.TestSchema;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.TestSchema.IdMessage;
import org.corfudb.test.TestSchema.ManagedResources;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster;
import org.corfudb.universe.universe.node.client.CorfuClient;
import org.corfudb.universe.universe.node.server.corfu.CorfuServer;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.api.universe.UniverseParams;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusUnavailable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;

@Slf4j
@Tag(TestGroups.STATEFUL)
public class KillServiceOnThreeNodesFiveHundredTimesParallelTest extends AbstractCorfuUniverseTest {
    private static final int LOOP_COUNT = 500;

    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
     * <p>
     * Test cluster behavior after kill service on all nodes
     * 1)  Deploy and bootstrap a three nodes cluster
     * 2)  Create a table in corfu
     * 3)  Repeat the steps from (4 - 9) in loop, LOOP_COUNT i.e 500
     * 4)  Add 100 Entries into table
     * 5)  if (LOOP_COUNT mod 2) equals to 0 then update the table entries
     * 6)  Verify the table rows and its contents
     * 7)  Stop the "corfu" service on all cluster nodes (parallel) then wait till cluster goes into "UNAVAILABLE" state
     * 8)  Start the "corfu" service on all cluster nodes (parallel) and wait till cluster status become "STABLE"
     * 9)  Verify the table rows and its content
     * 10)  Once the loop is over, verify all data
     * 11)  Clear the table contents
     */
    @Test
    public void test() {
        testRunner.executeStatefulVmTest(this::verifyKillServiceOnThreeNodesFiveHundredTimesParallelTest);
    }

    private void verifyKillServiceOnThreeNodesFiveHundredTimesParallelTest(
            UniverseWorkflow<UniverseParams, Fixture<UniverseParams>> wf) throws Exception {

        int start = 0;
        int count = 100;
        int end;
        boolean isTrue = false;

        final int numNodes = 3;

        UniverseParams params = wf.getFixture().data();
        CorfuCluster corfuCluster = wf.getUniverse()
                .getGroup(params.getGroupParamByIndex(0).getName());
        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        // Get the servers list
        List<CorfuServer> servers = IntStream.range(0, numNodes)
                .mapToObj(corfuCluster::getServerByIndex)
                .collect(Collectors.toList());

        //Check CLUSTER STATUS
        log.info("**** before running testcase, verify cluster status ****");
        waitForClusterStatusStable(corfuClient);
        log.info("*** cluster status is STABLE, executing testcase ***");

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore = new CorfuStore(runtime);

        // Define a namespace for the table.
        String manager = "manager";
        // Define table name
        String tableName = getClass().getSimpleName();

        // Create & Register the table.
        // This is required to initialize the table for the current corfu client.
        final Table<IdMessage, EventInfo, ManagedResources> table = UfoUtils.createTable(
                corfuStore, manager, tableName
        );

        List<TestSchema.IdMessage> uuids = new ArrayList<>();
        List<TestSchema.EventInfo> events = new ArrayList<>();
        TestSchema.ManagedResources metadata = TestSchema.ManagedResources.newBuilder()
                .setCreateUser("MrProto")
                .build();
        // Creating a transaction builder.
        final TxBuilder tx = corfuStore.tx(manager);

        for (int lcount = 1; lcount < LOOP_COUNT; lcount++) {

            end = count * lcount;
            log.info("*********************");
            log.info(String.format("*** required values like start::%s, end::%s and lcount::%s", start, end, lcount));
            log.info("*********************");
            log.info("*** insert the 100 enteries inot the table ***");
            UfoUtils.generateDataAndCommit(start, end, tableName, uuids, events, tx, metadata, isTrue);

            if (lcount % 2 == 0) {
                isTrue = true;
                log.info(" *** updating the records with start::{}, end::{} and lcount::{} ***", start, end, lcount);
                UfoUtils.generateDataAndCommit(start, end, tableName, uuids, events, tx, metadata, isTrue);
            }

            // verification of table rows and it's content one by one
            log.info(String.format("*** verify the rows count that should be %s ***", count * lcount));
            UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count * lcount);
            log.info(String.format("table has %s rows as expected", count * lcount));
            UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, isTrue);

            // Concurrently execute the command (kill service 'corfu') on all cluster nodes
            ExecutorService executor = Executors.newFixedThreadPool(numNodes);
            servers.forEach(node -> {
                Runnable killService = node::kill;
                executor.submit(killService);
            });

            log.info("*** wait for cluster status to become UNAVAILABLE ***");
            waitForClusterStatusUnavailable(corfuClient);

            // Concurrently execute the command (start service) on all cluster nodes
            log.info("*** starting 'corfu' service on all cluster nodes ***");
            servers.forEach(node -> {
                Runnable startService = node::start;
                executor.submit(startService);
            });

            waitForUnresponsiveServersChange(size -> size == 0, corfuClient);
            log.info("*** wait for cluster status to become STABLE ***");
            waitForClusterStatusStable(corfuClient);

            // verification of table rows and it's content one by one
            UfoUtils.verifyTableRowCount(corfuStore, manager, tableName, count * lcount);
            UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, isTrue);
            log.info(String.format("**** %s:: verification done ****", lcount));

            start = end;
            isTrue = false;

        }

        log.info(" *** at last, verifying the entire table contents on by one ***");
        start = 1;
        for (int idx = 1; idx < LOOP_COUNT; idx++) {
            end = count * idx;
            if (idx % 2 == 0) {
                log.info("*** verifying updated data ***");
                log.info(String.format("*** required values like start::%s, end::%s and lcount::%s", start, end, idx));
                UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, true);
            } else {
                log.info("*** verifying non-updated data ***");
                log.info(String.format("*** required values like start::%s, end::%s and lcount::%s", start, end, idx));
                UfoUtils.verifyTableData(corfuStore, start, end, manager, tableName, false);
            }
            start = end;
        }

        log.info("*** clearing up the table contents ***");
        Query q = corfuStore.query(manager);
        UfoUtils.clearTableAndVerify(table, tableName, q);

    }
}