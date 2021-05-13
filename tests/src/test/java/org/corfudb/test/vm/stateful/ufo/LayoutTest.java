package org.corfudb.test.vm.stateful.ufo;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.universe.UniverseManager.UniverseWorkflow;
import org.corfudb.universe.group.Group.GroupParams;
import org.corfudb.universe.group.cluster.CorfuCluster;
import org.corfudb.universe.group.cluster.vm.RemoteOperationHelper;
import org.corfudb.universe.node.Node;
import org.corfudb.universe.node.Node.NodeParams;
import org.corfudb.universe.node.client.CorfuClient;
import org.corfudb.universe.node.server.vm.VmCorfuServer;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.universe.UniverseParams;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusDegraded;

@Slf4j
@Tag(TestGroups.BAT)
@Tag(TestGroups.STATEFUL)
public class LayoutTest extends AbstractCorfuUniverseTest {

    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test.management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test.management.Shutdown
     * <p>
     * Test cluster behavior after add/remove nodes
     * 1) Deploy and bootstrap a three nodes cluster
     * 2) Verify Cluster is stable after deployment
     * 3) Create a table in corfu i.e. "CorfuUFO_AddAndRemoveServerTable"
     * 4) Add 100 Entries into table
     * 5) Verification by number of rows count i.e (Total rows: 100) and verify table content
     * 6) Remove one node from cluster
     * 7) Verify Layout, in layout there should be entry of node which we removed
     * 8) Verfication by number of rows count i.e (Total rows: 100) and verify table content
     * 9) Update the table enteirs from 60 to 90
     * 10) Add node back into cluster
     * 11) Add more 100 Entries into table and verify count and data of table
     * 12) Verfication by number of rows count i.e (Total rows: 200) and verify table content and
     * updated content as well
     * 13) Verify layout, detached node entry should be there
     * 14) Verify cluster status is stable or not
     * 15) Clear the table and verify table contents are cleared
     */

    @Test
    public void test() {
        testRunner.executeTest(this::renameLayoutCurrent);
    }

    private void renameLayoutCurrent(UniverseWorkflow<Fixture<UniverseParams>> wf) throws Exception {

        CorfuCluster<Node, GroupParams<NodeParams>> corfuCluster = wf.getUniverse()
                .getGroup(wf.getFixture().data().getGroupParamByIndex(0).getName());

        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        //CorfuRuntime runtime = corfuClient.getRuntime();

        //-------------------------- logic that we need to check
        VmCorfuServer vm = (VmCorfuServer) corfuCluster.getServerByIndex(2);
        RemoteOperationHelper commandHelper = vm.getRemoteOperationHelper();
        // get the file 'LAYOUT_CURRENT.ds' path
        String layoutFilePath = commandHelper.executeCommand("find ~/ -name LAYOUT_CURRENT.ds -exec dirname {} \\;");
        log.info(String.format(" *** layout file path is:: %s ***",  layoutFilePath));
        log.info(" *** rename LAYOUT_CURRENT ***");
        // String cmd =  "cd " + layoutFilePath + "; mv LAYOUT_CURRENT.ds _LAYOUT_CURRENT.ds \"";
        String cmd = String.format("cd %s ; mv LAYOUT_CURRENT.ds _LAYOUT_CURRENT.ds", layoutFilePath.trim());
        log.info(String.format(" *** executing command:: %s ***",  cmd));
        commandHelper.executeCommand(cmd);
        log.info(" *** rename LAYOUT_CURRENT done ***");
        log.info(" *** starting corfu process ***");
        vm.restart();
        log.info(" *** starting corfu process done ***");
        // corfuCluster = wf.getUniverse()
        //        .getGroup(params.getGroupParamByIndex(0).getName());
        // corfuClient = corfuCluster.getLocalCorfuClient();
        waitForClusterStatusDegraded(corfuClient);
    }
}
