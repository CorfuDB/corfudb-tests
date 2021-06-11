package org.corfudb.test.vm.stateful.ufo.reboot;

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
import org.corfudb.test.spec.api.GenericSpec.SpecHelper;
import org.corfudb.test.TestSchema.ManagedResources;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.cluster.Cluster.ClusterType;
import org.corfudb.universe.api.workflow.UniverseWorkflow;
import org.corfudb.universe.infrastructure.vm.universe.node.server.VmCorfuServer;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster.GenericCorfuCluster;
import org.corfudb.universe.universe.node.client.CorfuClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;
import static org.corfudb.universe.test.util.ScenarioUtils.waitUninterruptibly;

@Slf4j
@Tag(TestGroups.STATEFUL)
public class RebootOneNodeThousandTimesTest extends AbstractCorfuUniverseTest {
    private static final int LOOP_COUNT = 1000;

    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
     * <p>
     * Test cluster behavior after reboot node in loop for 1000 times
     * 1) Deploy and bootstrap a three nodes cluster
     * 2) Create a table in corfu
     * 3) Verify cluster status (should be STABLE)
     * 4) Add 100 Entries into table and verify count and data of table
     * 5) Get random node index
     * 6) Reboot that node then wait for 30sec before starting the 'corfu' process on rebooted node
     * 7) Wait for Cluster status ( should be STABLE) and add Entry into Table
     * 8) Repeat steps 6-7 500 Times
     * 9) Verify count and data of table
     * 10) Add 100 more entries into table
     * 11) Update Records from 60 to 139 index and Verify
     * 12) Verify count and data of table
     * 13) Clear the table and verify table contents are cleared
     */
    @Test
    public void test() {
        testRunner.executeStatefulVmTest(this::verifyRebootNode);
    }

    private void verifyRebootNode(UniverseWorkflow<UniverseParams, Fixture<UniverseParams>> wf)
            throws Exception {

        // create instance of Random class
        Random rand = new SecureRandom();

        GenericCorfuCluster corfuCluster = wf.getUniverse().getGroup(ClusterType.CORFU);
        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore = new CorfuStore(runtime);


        // Define table name
        String tableName = getClass().getSimpleName();
        SpecHelper helper = new SpecHelper(runtime, tableName);
        List<TestSchema.IdMessage> uuids = new ArrayList<>();
        List<TestSchema.EventInfo> events = new ArrayList<>();
// Actual Testcase Starts and defining initial Row count for Table
        final int count = 100;

        log.info("**** Checking cluster status ****");
        waitForClusterStatusStable(corfuClient);

        log.info("**** Add 1st set of 100 entries ****");
        helper.transactional((utils, txn) -> utils.generateData(0, count, uuids, events, txn, false));
        helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count));

        log.info("**** First Insertion Verification:: Verify Table Data one by one ****");
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, 0, count, false));
        log.info("**** First Insertion Verified... ****");

        // Reboot the single node in cluster
        for (int loopCount = 1; loopCount <= LOOP_COUNT; loopCount++) {
            log.info("**** In Loop :: " + loopCount + " ****");
            // get the random node
            int rindex = rand.nextInt(3);

            VmCorfuServer server = (VmCorfuServer) corfuCluster.getServerByIndex(rindex);
            log.info(String.format("**** Rebooting node:: %s ****", server.getIpAddress()));
            server.getVmManager().reboot();

            log.info(" *** wait for 30sec, after rebooting the node ***");
            waitUninterruptibly(Duration.ofSeconds(30));

            log.info(String.format(" *** starting 'corfu' process on node:: %s ***", server.getIpAddress()));
            server.start();

            waitForUnresponsiveServersChange(size -> size == 0, corfuClient);
            log.info("**** Verify cluster status STABLE  ****");
            waitForClusterStatusStable(corfuClient);

            log.info("**** Add entry whilst in loop ****");
            int finalLoopCount = loopCount;
            helper.transactional((utils, txn) -> utils.generateData((count + finalLoopCount) - 1,
                    count + finalLoopCount, uuids, events, txn, false));
        }

        log.info("**** Second Insertion Verification:: Verify Table Rows and Data one by one ****");
        helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count + LOOP_COUNT));
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, count, count + LOOP_COUNT, false));
        log.info("**** Second Insertion Verified... ****");

        log.info("**** Add last set of 100 entries ****");
        helper.transactional((utils, txn) -> utils.generateData(count + LOOP_COUNT,
                count * 2 + LOOP_COUNT, uuids, events, txn, false));

        log.info("**** Third Insertion Verification:: Verify Table Rows and Data one by one ****");
        helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count * 2 + LOOP_COUNT));
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, 0, count * 2 + LOOP_COUNT, false));
        log.info("**** Third Insertion Verified... **** ");

        log.info("**** Update the records **** ");
        helper.transactional((utils, txn) -> utils.generateData(60, 140, uuids, events, txn, true));

        log.info("**** Fourth Insertion Verification:: Verify Table Data one by one ****");
        helper.transactional((utils, txn) -> utils.verifyTableRowCount(txn, count * 2 + LOOP_COUNT));
        helper.transactional((utils, txn) -> utils.verifyTableData(txn, 60, 140, true));

        log.info("**** Clear the Table ****");

        helper.transactional(UfoUtils::clearTableAndVerify);
    }
}
