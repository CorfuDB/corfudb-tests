package org.corfudb.universe.test.util;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.collections.CorfuTable;
import org.corfudb.runtime.exceptions.UnreachableClusterException;
import org.corfudb.runtime.view.ClusterStatusReport;
import org.corfudb.runtime.view.ClusterStatusReport.ClusterStatus;
import org.corfudb.runtime.view.ClusterStatusReport.NodeStatus;
import org.corfudb.runtime.view.Layout;
import org.corfudb.runtime.view.ManagementView;
import org.corfudb.universe.node.client.ClientParams;
import org.corfudb.universe.node.client.CorfuClient;
import org.corfudb.universe.node.server.CorfuServer;
import org.corfudb.universe.scenario.fixture.Fixtures.TestFixtureConst;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.corfudb.universe.scenario.fixture.Fixtures.TestFixtureConst.DEFAULT_WAIT_TIME;

@Slf4j
public class ScenarioUtils {

    private ScenarioUtils() {
        //prevent creating instances
    }

    /**
     * Waits until epoch get changed in the layout
     *
     * @param corfuClient corfu client
     * @param nextEpoch   expected epoch
     * @throws InterruptedException if thread is interrupted
     */
    public static void waitForNextEpoch(CorfuClient corfuClient, long nextEpoch)
            throws InterruptedException {

        waitForLayoutChange(layout -> {
            if (layout.getEpoch() > nextEpoch) {
                String errMsg = String.format(
                        "Layout epoch is ahead of next epoch. Next epoch: %d, layout epoch: %d",
                        nextEpoch, layout.getEpoch()
                );
                throw new IllegalStateException(errMsg);
            }
            return layout.getEpoch() == nextEpoch;
        }, corfuClient);
    }

    /**
     * Refreshes the layout and waits for a limited time for the refreshed layout to
     * satisfy the expected verifier.
     *
     * @param verifier    Layout predicate to test the refreshed layout.
     * @param corfuClient corfu client.
     */
    public static void waitForLayoutChange(Predicate<Layout> verifier, CorfuClient corfuClient)
            throws InterruptedException {

        corfuClient.invalidateLayout();
        Layout refreshedLayout = corfuClient.getLayout();

        for (int i = 0; i < TestFixtureConst.DEFAULT_WAIT_POLL_ITER; i++) {
            if (verifier.test(refreshedLayout)) {
                break;
            }
            corfuClient.invalidateLayout();
            refreshedLayout = corfuClient.getLayout();
            sleep();
        }

        assertThat(verifier.test(refreshedLayout)).isTrue();
    }

    /**
     * Refreshes the layout and waits for a limited time for the refreshed layout to
     * satisfy the expected unresponsive servers size
     *
     * @param verifier    IntPredicate to test the refreshed unresponsive servers size
     * @param corfuClient corfu client.
     */
    public static void waitForUnresponsiveServersChange(
            IntPredicate verifier, CorfuClient corfuClient) throws InterruptedException {

        corfuClient.invalidateLayout();
        Layout refreshedLayout = corfuClient.getLayout();

        for (int i = 0; i < TestFixtureConst.DEFAULT_WAIT_POLL_ITER; i++) {
            if (verifier.test(refreshedLayout.getUnresponsiveServers().size())) {
                break;
            }
            corfuClient.invalidateLayout();
            refreshedLayout = corfuClient.getLayout();
            sleep();
        }

        assertThat(verifier.test(refreshedLayout.getUnresponsiveServers().size())).isTrue();
    }

    /**
     * Refreshes the layout and waits for a limited time for the refreshed layout to
     * satisfy the expected all layout servers size
     *
     * @param verifier    IntPredicate to test the refreshed layout servers size
     * @param corfuClient corfu client.
     */
    public static void waitForLayoutServersChange(IntPredicate verifier, CorfuClient corfuClient)
            throws InterruptedException {

        corfuClient.invalidateLayout();
        Layout refreshedLayout = corfuClient.getLayout();

        for (int i = 0; i < TestFixtureConst.DEFAULT_WAIT_POLL_ITER; i++) {
            if (verifier.test(refreshedLayout.getAllServers().size())) {
                break;
            }
            corfuClient.invalidateLayout();
            refreshedLayout = corfuClient.getLayout();
            sleep();
        }

        assertThat(verifier.test(refreshedLayout.getAllServers().size())).isTrue();
    }

    private static void sleep() throws InterruptedException {
        TimeUnit.SECONDS.sleep(DEFAULT_WAIT_TIME.getSeconds());
    }


    /**
     * Wait for cluster status to become STABLE
     *
     * @param corfuClient corfu client.
     */
    public static void waitForClusterStatusStable(CorfuClient corfuClient)
            throws InterruptedException {

        ClusterStatusReport clusterStatusReport = corfuClient
                .getManagementView()
                .getClusterStatus();

        while (clusterStatusReport.getClusterStatus() != ClusterStatus.STABLE) {
            clusterStatusReport = corfuClient.getManagementView().getClusterStatus();
            waitUninterruptibly(Duration.ofSeconds(3));
        }
        assertThat(clusterStatusReport.getClusterStatus()).isEqualTo(ClusterStatus.STABLE);
    }

    /**
     * Wait for cluster status to become DEGRADED
     *
     * @param corfuClient corfu client.
     */
    public static void waitForClusterStatusDegraded(CorfuClient corfuClient)
            throws InterruptedException {
        ClusterStatusReport clusterStatusReport = corfuClient
                .getManagementView()
                .getClusterStatus();
        while (clusterStatusReport.getClusterStatus() != ClusterStatus.DEGRADED) {
            clusterStatusReport = corfuClient.getManagementView().getClusterStatus();
            waitUninterruptibly(Duration.ofSeconds(10));
        }
        assertThat(clusterStatusReport.getClusterStatus()).isEqualTo(ClusterStatus.DEGRADED);
    }

    /**
     * Wait for cluster status to become UNAVAILABLE
     *
     * @param corfuClient corfu client.
     */
    public static void waitForClusterStatusUnavailable(CorfuClient corfuClient)
            throws InterruptedException {

        ManagementView managementView = corfuClient.getManagementView();
        ClusterStatusReport clusterStatusReport = managementView.getClusterStatus();

        while (clusterStatusReport.getClusterStatus() != ClusterStatus.UNAVAILABLE) {
            clusterStatusReport = managementView.getClusterStatus();
            waitUninterruptibly(Duration.ofSeconds(10));
        }
        assertThat(clusterStatusReport.getClusterStatus()).isEqualTo(ClusterStatus.UNAVAILABLE);
    }

    /**
     * Wait for Standalone node cluster status table
     *
     * @param corfuClient corfu client
     */

    public static void waitForStandaloneNodeClusterStatusStable(
            CorfuClient corfuClient, CorfuServer node) throws InterruptedException {

        ClusterStatusReport clusterStatusReport = node.getLocalCorfuClient()
                .getManagementView()
                .getClusterStatus();
        while (clusterStatusReport.getClusterStatus() != ClusterStatus.STABLE) {
            clusterStatusReport = corfuClient.getManagementView().getClusterStatus();
            waitUninterruptibly(Duration.ofSeconds(10));
        }
        assertThat(clusterStatusReport.getClusterStatus()).isEqualTo(ClusterStatus.STABLE);
    }

    /**
     * Wait for failure detector to detect the cluster is down by generating a write request.
     * The runtime's systemDownHandler will be invoked after a limited time of retries
     * This method should only be called only after the cluster is unavailable
     *
     * @param table CorfuTable to generate write request
     */
    @SuppressWarnings("unchecked")
    public static void waitForClusterDown(CorfuTable table) {
        try {
            table.put(new Object(), new Object());
            fail("Cluster should already be down");
        } catch (UnreachableClusterException e) {
            log.info("Successfully waited failure detector to detect cluster down");
        }
    }

    /**
     * Waiting until a cluster is up
     *
     * @param table table name
     * @param value value
     * @throws InterruptedException when the method get interrupted
     */
    public static void waitForClusterUp(CorfuTable table, String value)
            throws InterruptedException {

        for (int i = 0; i < 3; i++) {
            try {
                table.get(value);
                return;
            } catch (UnreachableClusterException e) {
                log.info("Successfully waited failure detector to detect cluster down");
            }

            waitUninterruptibly(Duration.ofSeconds(10));
        }
    }

    /**
     * Wait for a specific amount of time. This should only be used when there is nothing
     * else we can wait on, e.g. no layout change, no cluster status change.
     *
     * @param duration duration to wait
     */
    public static void waitUninterruptibly(Duration duration) throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(duration.toMillis());
    }

    /**
     * Verify Unresponsive servers
     *
     * @param corfuClient corfu client
     * @param server      corfu server
     */
    public static void verifyUnresponsiveServers(CorfuClient corfuClient, String server) {
        assertThat(corfuClient.getLayout().getUnresponsiveServers())
                .containsExactly(server);
    }

    /**
     * Verify Node status is down or not
     *
     * @param corfuClient corfu client
     * @param server      corfu server
     */
    public static void verifyNodeStatusIsDown(CorfuClient corfuClient, CorfuServer server) {
        ClusterStatusReport clusterStatusReport = corfuClient
                .getManagementView()
                .getClusterStatus();
        Map<String, NodeStatus> statusMap = clusterStatusReport.getClusterNodeStatusMap();
        assertThat(statusMap.get(server.getEndpoint())).isEqualTo(NodeStatus.DOWN);
    }


    /**
     * Detach the node and verify the cluster status
     *
     * @param corfuClient   corfu client
     * @param server        corfu server
     * @param clientFixture client params
     */
    public static void detachNodeAndVerify(
            CorfuClient corfuClient, CorfuServer server, ClientParams clientFixture)
            throws InterruptedException {

        //Remove corfu node from the corfu cluster (layout)
        log.info("Remove Node from Cluster");
        corfuClient.getManagementView().removeNode(
                server.getEndpoint(),
                clientFixture.getNumRetry(),
                clientFixture.getTimeout(),
                clientFixture.getPollPeriod()
        );

        log.info("Reset the Detached Node");
        // Reset the detached node so that we do not end up with an OverwriteException.
        corfuClient.getRuntime().getLayoutView().getRuntimeLayout()
                .getBaseClient(server.getEndpoint()).reset();

        // Verify layout contains only the nodes that is not removed
        corfuClient.invalidateLayout();
        assertThat(corfuClient.getLayout().getAllServers())
                .doesNotContain(server.getEndpoint());

        //Check CLUSTER STATUS
        log.info("Checking cluster status is STABLE");
        waitForClusterStatusStable(corfuClient);
    }

    /**
     * Add the node and verify the cluster status
     *
     * @param corfuClient   corfu client
     * @param server        corfu server
     * @param clientFixture client parameters
     */
    public static void addNodeAndVerify(
            CorfuClient corfuClient, CorfuServer server, ClientParams clientFixture)
            throws InterruptedException {

        //Add corfu node back to the cluster
        log.info("Add Node to Cluster");
        corfuClient.getManagementView().addNode(
                server.getEndpoint(),
                clientFixture.getNumRetry(),
                clientFixture.getTimeout(),
                clientFixture.getPollPeriod()
        );
        // Verify layout contains the node that is added
        corfuClient.invalidateLayout();
        assertThat(corfuClient.getLayout().getAllServers())
                .contains(server.getEndpoint());

        //Check CLUSTER STATUS
        log.info("Checking cluster status is STABLE");
        waitForClusterStatusStable(corfuClient);
    }
}
