package org.corfudb.test.vm.stateful.ufo;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.runtime.collections.Query;
import org.corfudb.runtime.collections.Table;
import org.corfudb.runtime.collections.TxBuilder;
import org.corfudb.runtime.view.ClusterStatusReport;
import org.corfudb.runtime.view.ClusterStatusReport.ConnectivityStatus;
import org.corfudb.runtime.view.ClusterStatusReport.NodeStatus;
import org.corfudb.test.TestGroups;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.TestSchema.IdMessage;
import org.corfudb.test.TestSchema.ManagedResources;
import org.corfudb.universe.UniverseManager;
import org.corfudb.universe.UniverseManager.UniverseWorkflow;
import org.corfudb.universe.group.cluster.CorfuCluster;
import org.corfudb.universe.group.cluster.CorfuClusterParams;
import org.corfudb.universe.node.client.CorfuClient;
import org.corfudb.universe.node.server.CorfuServer;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.UniverseConfigurator;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.UniverseParams;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.corfudb.runtime.view.ClusterStatusReport.ClusterStatus;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;
import static org.corfudb.universe.test.util.ScenarioUtils.waitUninterruptibly;

@Slf4j
@Tag(TestGroups.STATEFUL)
public class AllNodesPartitionedTest {

    /**
     * Cluster deployment/shutdown for a stateful test (on demand):
     * - deploy a cluster: run org.corfudb.universe.test..management.Deployment
     * - Shutdown the cluster org.corfudb.universe.test..management.Shutdown
     * <p>
     * Test cluster behavior after all nodes are partitioned symmetrically
     * 1) Deploy and bootstrap a three nodes cluster
     * 2) Verify Cluster is stable after deployment
     * 3) Create a table in corfu i.e. "CorfuUFO_AllNodesPartitionedTable"
     * 4) Add 100 Entries into table
     * 5) Verification by number of rows count i.e (Total rows: 100) and verify table content
     * 6) Symmetrically partition all nodes so that they can't communicate
     * to any other node in cluster and vice versa
     * 7) Verify Layout, in layout there should be entry of node which we removed
     * 8) Recover cluster by reconnecting the partitioned node
     * 9) Verify layout, cluster status and data path again
     * 10) Add more 100 Entries into table and verify count and data of table
     * 11) Verification by number of rows count i.e (Total rows: 200) and verify table content
     * 12) Update the table entries from 60 to 90
     * 13) verify table content updated content
     * 14) clear the contents of the table
     */

    private final UniverseConfigurator configurator = UniverseConfigurator.builder().build();
    private final UniverseManager universeManager = configurator.universeManager;

    @Test
    public void test() {

        universeManager.workflow(wf -> {
            wf.setupVm(configurator.vmSetup);
            wf.setupVm(fixture -> {
                //don't stop corfu cluster after the test
                fixture.getUniverse().cleanUpEnabled(false);
            });
            wf.initUniverse();
            try {
                verifyAllNodesPartitioned(wf);
            } catch (Exception e) {
                fail("Failed: ", e);
            }
        });
    }

    private void verifyAllNodesPartitioned(UniverseWorkflow<Fixture<UniverseParams>> wf)
            throws Exception {
        UniverseParams params = wf.getFixture().data();

        CorfuCluster<CorfuServer, CorfuClusterParams> corfuCluster = wf.getUniverse()
                .getGroup(params.getGroupParamByIndex(0).getName());

        CorfuClusterParams corfuClusterParams = corfuCluster.getParams();

        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore = new CorfuStore(runtime);

        // Define a namespace for the table.
        String namespace = "manager";
        // Define table name
        String tableName = "CorfuUFO_AllNodesPartitionedTable";

        assertThat(corfuCluster.nodes().size()).isEqualTo(3);
        assertThat(corfuCluster.nodes().size()).isEqualTo(corfuClusterParams.size());

        assertThat(corfuCluster.getParams().getNodesParams().size())
                .as("Invalid cluster: %s, but expected 3 nodes",
                        corfuClusterParams.getClusterNodes()
                )
                .isEqualTo(3);

        final Table<IdMessage, EventInfo, ManagedResources> table = UfoUtils.createTable(
                corfuStore, namespace, tableName
        );

        final int count = 100;
        final List<IdMessage> uuids = new ArrayList<>();
        final List<EventInfo> events = new ArrayList<>();
        final ManagedResources metadata = ManagedResources.newBuilder()
                .setCreateUser("MrProto")
                .build();
        // Creating a transaction builder.
        TxBuilder tx = corfuStore.tx(namespace);

        final Query q = corfuStore.query(namespace);
        UfoUtils.generateDataAndCommit(0, count, tableName, uuids, events, tx, metadata, false);

        log.info("First Verification:: Verify table row count");
        UfoUtils.verifyTableRowCount(corfuStore, namespace, tableName, count);
        log.info("First Verification:: Verify Table Data one by one");
        UfoUtils.verifyTableData(corfuStore, 0, count, namespace, tableName, false);
        log.info("First Verification:: Completed");

        // Symmetrically partition all nodes and wait for failure
        // detector to work and cluster to stabilize
        List<CorfuServer> allServers = corfuCluster.nodes().values().asList();
        allServers.forEach(server -> {
            List<CorfuServer> otherServers = new ArrayList<>(allServers);
            otherServers.remove(server);
            server.disconnect(otherServers);
        });

        waitUninterruptibly(Duration.ofSeconds(20));

        // Verify cluster and node status
        ClusterStatusReport clusterStatusReport = corfuClient
                .getManagementView()
                .getClusterStatus();
        assertThat(clusterStatusReport.getClusterStatus()).isEqualTo(ClusterStatus.STABLE);

        Map<String, NodeStatus> statusMap = clusterStatusReport.getClusterNodeStatusMap();
        corfuCluster.nodes()
                .values()
                .forEach(node -> assertThat(statusMap.get(node.getEndpoint())).isEqualTo(NodeStatus.UP));

        Map<String, ConnectivityStatus> connectivityMap = clusterStatusReport
                .getClientServerConnectivityStatusMap();

        corfuCluster.nodes().values().forEach(node -> {
            assertThat(connectivityMap.get(node.getEndpoint()))
                    .isEqualTo(ConnectivityStatus.RESPONSIVE);
        });

        // Remove partitions and wait for layout's unresponsive servers to change
        waitUninterruptibly(Duration.ofSeconds(10));
        corfuCluster.nodes().values().forEach(CorfuServer::reconnect);

        waitForUnresponsiveServersChange(size -> size == 0, corfuClient);

        // Verify cluster status is STABLE
        log.info("Verify Check cluster status");
        waitForClusterStatusStable(corfuClient);

        UfoUtils.generateDataAndCommit(
                100, count * 2, tableName, uuids, events, tx, metadata, false
        );
        log.info("Second Verification:: Verify table row count");
        UfoUtils.verifyTableRowCount(corfuStore, namespace, tableName, count * 2);
        log.info("Second Verification:: Verify Table Data one by one");
        UfoUtils.verifyTableData(corfuStore, 100, count * 2, namespace, tableName, false);
        log.info("Second Verification:: Completed");

        log.info("Update the records");
        UfoUtils.generateDataAndCommit(60, 90, tableName, uuids, events, tx, metadata, true);

        log.info("Third Verification:: Verify the data");
        UfoUtils.verifyTableData(corfuStore, 0, 60, namespace, tableName, false);
        UfoUtils.verifyTableData(corfuStore, 91, count * 2, namespace, tableName, false);

        log.info("Third Verification:: Verify the updated data");
        UfoUtils.verifyTableData(corfuStore, 60, 90, namespace, tableName, true);
        log.info("Third Verification:: Completed");

        UfoUtils.clearTableAndVerify(table, tableName, q);

    }
}