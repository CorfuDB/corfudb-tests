package org.corfudb.test.spec;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.test.TestSchema;
import org.corfudb.test.TestSchema.IdMessage;
import org.corfudb.test.spec.api.GenericSpec;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.api.universe.node.ApplicationServers.CorfuApplicationServer;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster.GenericCorfuCluster;
import org.corfudb.universe.universe.node.client.CorfuClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForLayoutChange;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;
import static org.corfudb.universe.test.util.ScenarioUtils.waitUninterruptibly;

/**
 * Cluster deployment/shutdown for a stateful test (on demand):
 * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
 * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
 * <p>
 * Test cluster behavior after one node down and another partitioned
 * 1) Deploy and bootstrap a three nodes cluster
 * 2) Create a table in corfu
 * 3) Add 100 Entries into table and verify count and data of table
 * 4) Symmetrically partition one node
 * 5) Verify layout, cluster status and data path
 * 6) Recover cluster by restart the stopped node and fix partition
 * 7) Verify layout, cluster status and data path
 * 8) Add 100 more Entries into table and verify count and data of table
 * 9) Update Records from 51 to 150 index and verify
 * 10) Verify all 200 rows data
 * 11) Clear the table and verify table contents are cleared
 */
@Slf4j
public class NodeDownAndPartitionedSpec {

    /**
     * verifyNodeDownAndPartitioned
     *
     * @param wf universe workflow
     * @throws Exception error
     */
    public <P extends UniverseParams, F extends Fixture<P>, U extends UniverseWorkflow<P, F>> void downAndPartitioned(
            U wf) throws Exception {

        GenericCorfuCluster corfuCluster = wf.getUniverse().getGroup(ClusterType.CORFU);
        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        //Check CLUSTER STATUS
        log.info("**** Checking cluster status ****");
        waitForClusterStatusStable(corfuClient);

        CorfuRuntime runtime = corfuClient.getRuntime();

        // Define table name
        String tableName = getClass().getSimpleName();

        GenericSpec.SpecHelper helper = new GenericSpec.SpecHelper(runtime, tableName);


        final int count = 100;
        List<IdMessage> uuids = new ArrayList<>();
        List<TestSchema.EventInfo> events = new ArrayList<>();

        // Add data in table (100 entries)
        log.info("**** Adding 1st set of 100 entries ****");
        helper.transactional((utils, txn) -> utils.generateData(0, count, uuids, events, txn, false));
        helper.transactional((utils, txn) -> {
            // Verify table row count (should be 100)
            utils.verifyTableRowCount(txn, count);
            log.info("**** First Insertion Verification:: Verify Table Data one by one ****");
            utils.verifyTableData(txn, 0, count, false);
            log.info("**** First Insertion Verified... ****");
        });

        // Get all nodes of cluster in separate variables
        CorfuApplicationServer server0 = corfuCluster.getFirstServer();
        CorfuApplicationServer server1 = corfuCluster.getServerByIndex(1);
        CorfuApplicationServer server2 = corfuCluster.getServerByIndex(2);

        // Stop one node and partition another one
        log.info("**** Stop node server1 ****");
        server1.stop(Duration.ofSeconds(60));
        log.info("**** Disconnect node server2 ****");
        server2.disconnect(Arrays.asList(server0, server1));
        waitUninterruptibly(Duration.ofSeconds(30));
        // Verify cluster status
        corfuClient.invalidateLayout();
        log.info("**** Verify cluster status :: after stopping and disconnecting nodes ****");
        waitForClusterStatusStable(corfuClient);

        // Restart the stopped node and wait for layout's unresponsive servers to change
        log.info("**** Restart node server1 ****");
        server1.start();
        // Verify layout
        waitForLayoutChange(layout -> layout.getUnresponsiveServers()
                .equals(Collections.singletonList(server2.getEndpoint())), corfuClient);
        // removing partition
        log.info("**** Reconnect node server2 ****");
        server2.reconnect(Arrays.asList(server0, server1));
        waitForUnresponsiveServersChange(size -> size == 0, corfuClient);
        // Verify cluster status is STABLE
        log.info("**** Verify cluster status after restarting node and removing partition ****");
        waitForClusterStatusStable(corfuClient);

        // Add 100 more entries in table
        log.info("**** Adding 2nd set of 100 entries ****");
        helper.transactional((utils, txn) -> utils.generateData(100, 200, uuids, events, txn, false));
        helper.transactional((utils, txn) -> {
            // Verify table row count (should be 200)
            utils.verifyTableRowCount(txn, 200);
            log.info("**** Second Insertion Verification:: Verify Table Data one by one ****");
            utils.verifyTableData(txn, 0, 200, false);
            log.info("**** Second Insertion Verified... ****");
        });

        //Update table records from 51 to 150
        log.info("**** Update the records ****");
        helper.transactional((utils, txn) -> utils.generateData(51, 150, uuids, events, txn, true));
        helper.transactional((utils, txn) -> {
            // Verify table row count (should be 200)
            utils.verifyTableRowCount(txn, count * 2);
            log.info("**** Record Updation Verification:: Verify Table Data one by one ****");
            utils.verifyTableData(txn, 51, 150, true);
            log.info("**** Record Updation Verified ****");

            // Verify all data in table
            log.info("**** Verify Table Data (200 rows) one by one ****");
            utils.verifyTableData(txn, 0, 51, false);
            utils.verifyTableData(txn, 151, count * 2, false);
            utils.verifyTableData(txn, 51, 150, true);
        });

        // Clear table data and verify
        helper.transactional(UfoUtils::clearTableAndVerify);
    }
}
