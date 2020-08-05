package org.corfudb.test.docker;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.corfudb.runtime.collections.CorfuTable;
import org.corfudb.runtime.view.ClusterStatusReport;
import org.corfudb.runtime.view.ClusterStatusReport.ClusterStatus;
import org.corfudb.runtime.view.Layout;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.universe.UniverseManager.UniverseWorkflow;
import org.corfudb.universe.group.cluster.CorfuCluster;
import org.corfudb.universe.node.client.CorfuClient;
import org.corfudb.universe.node.server.CorfuServer;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.universe.UniverseParams;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.corfudb.runtime.view.ClusterStatusReport.NodeStatus.DOWN;
import static org.corfudb.universe.scenario.fixture.Fixtures.TestFixtureConst.DEFAULT_STREAM_NAME;
import static org.corfudb.universe.scenario.fixture.Fixtures.TestFixtureConst.DEFAULT_TABLE_ITER;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;

@Slf4j
@Tag(TestGroups.BAT_DOCKER)
public class FileDescriptorLeaksTest extends AbstractCorfuUniverseTest {

    @Test
    public void test() {
        testRunner.executeDockerTest(this::verifyAddAndRemoveNode);
    }

    public void verifyAddAndRemoveNode(UniverseWorkflow<Fixture<UniverseParams>> wf) throws Exception {

        String groupName = wf.getFixture().data().getGroupParamByIndex(0).getName();
        CorfuCluster corfuCluster = wf.getUniverse().getGroup(groupName);

        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        CorfuTable<String, String> table = corfuClient
                .createDefaultCorfuTable(DEFAULT_STREAM_NAME);

        for (int i = 0; i < 1024; i++) {
            log.info("next iteration: " + i);
            table.put(String.valueOf(i), RandomStringUtils.randomAlphanumeric(1024 * 1024));
        }

        System.exit(0);

        //Should stop one node and then restart
        CorfuServer server0 = corfuCluster.getFirstServer();

        // Stop one node and wait for layout's unresponsive servers to change
        server0.stop(Duration.ofSeconds(10));
        waitForUnresponsiveServersChange(size -> size == 1, corfuClient);

        // Verify layout, unresponsive servers should contain only the stopped node
        Layout layout = corfuClient.getLayout();
        assertThat(layout.getUnresponsiveServers()).containsExactly(server0.getEndpoint());

        // Verify cluster status is DEGRADED with one node down
        ClusterStatusReport clusterStatusReport = corfuClient
                .getManagementView()
                .getClusterStatus();
        assertThat(clusterStatusReport.getClusterStatus()).isEqualTo(ClusterStatus.DEGRADED);

        Map<String, ClusterStatusReport.NodeStatus> statusMap = clusterStatusReport.getClusterNodeStatusMap();
        assertThat(statusMap.get(server0.getEndpoint())).isEqualTo(DOWN);

        // Verify data path working fine
        for (int i = 0; i < DEFAULT_TABLE_ITER; i++) {
            assertThat(table.get(String.valueOf(i))).isEqualTo(String.valueOf(i));
        }

        // restart the stopped node and wait for layout's unresponsive servers to change
        server0.start();
        waitForUnresponsiveServersChange(size -> size == 0, corfuClient);

        final Duration sleepDuration = Duration.ofSeconds(1);
        // Verify cluster status is STABLE
        clusterStatusReport = corfuClient.getManagementView().getClusterStatus();
        while (!clusterStatusReport.getClusterStatus().equals(ClusterStatus.STABLE)) {
            clusterStatusReport = corfuClient.getManagementView().getClusterStatus();
            TimeUnit.MILLISECONDS.sleep(sleepDuration.toMillis());
        }
        assertThat(clusterStatusReport.getClusterStatus()).isEqualTo(ClusterStatus.STABLE);

        // Verify data path working fine
        for (int i = 0; i < DEFAULT_TABLE_ITER; i++) {
            assertThat(table.get(String.valueOf(i))).isEqualTo(String.valueOf(i));
        }


    }
}
