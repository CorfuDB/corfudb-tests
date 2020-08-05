package org.corfudb.test.spec;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.CorfuStoreMetadata;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.runtime.collections.Query;
import org.corfudb.runtime.collections.Table;
import org.corfudb.runtime.collections.TxBuilder;
import org.corfudb.test.TestSchema.EventInfo;
import org.corfudb.test.TestSchema.IdMessage;
import org.corfudb.test.TestSchema.ManagedResources;
import org.corfudb.universe.UniverseManager;
import org.corfudb.universe.group.Group.GroupParams;
import org.corfudb.universe.group.cluster.CorfuCluster;
import org.corfudb.universe.node.Node;
import org.corfudb.universe.node.Node.NodeParams;
import org.corfudb.universe.node.client.ClientParams;
import org.corfudb.universe.node.client.CorfuClient;
import org.corfudb.universe.node.server.CorfuServer;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.test.util.UfoUtils;
import org.corfudb.universe.universe.UniverseParams;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForClusterStatusStable;

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
@Slf4j
public class AddAndRemoveServerSpec {

    /**
     * verifyAddAndRemoveNode
     * @param wf universe workflow
     * @throws Exception error
     */
    public void verifyAddAndRemoveNode(UniverseManager.UniverseWorkflow<Fixture<UniverseParams>> wf) throws Exception {

        CorfuCluster<Node, GroupParams<NodeParams>> corfuCluster = wf.getUniverse()
                .getGroup(wf.getFixture().data().getGroupParamByIndex(0).getName());

        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        CorfuRuntime runtime = corfuClient.getRuntime();
        // Creating Corfu Store using a connected corfu client.
        CorfuStore corfuStore = new CorfuStore(runtime);

        // Define a namespace for the table.
        String namespace = "manager";
        // Define table name
        String tableName = getClass().getSimpleName();

        log.info("Verify cluster status is stable");
        waitForClusterStatusStable(corfuClient);

        final Table<IdMessage, EventInfo, ManagedResources> table =
                UfoUtils.createTable(corfuStore, namespace, tableName);

        final int count = 100;
        final List<IdMessage> uuids = new ArrayList<>();
        final List<EventInfo> events = new ArrayList<>();
        final ManagedResources metadata = ManagedResources.newBuilder()
                .setCreateUser("MrProto")
                .build();
        // Creating a transaction builder.
        TxBuilder tx = corfuStore.tx(namespace);

        // Fetch timestamp to perform snapshot queries or transactions at a particular timestamp.
        CorfuStoreMetadata.Timestamp timestamp = corfuStore.getTimestamp();
        log.trace("Timestamp: {}", timestamp);

        UfoUtils.generateDataAndCommit(0, count, tableName, uuids, events, tx, metadata, false);
        final Query q = corfuStore.query(namespace);

        log.info("First Verification:: Verify table row count");
        UfoUtils.verifyTableRowCount(corfuStore, namespace, tableName, count);
        log.info("First Verification:: Verify Table Data one by one");
        UfoUtils.verifyTableData(corfuStore, 0, count, namespace, tableName, false);
        log.info("First Verification:: Completed");

        //Remove corfu node from the corfu cluster (layout)
        CorfuServer server0 = corfuCluster.getFirstServer();
        ClientParams clientFixture = ClientParams.builder().build();
        corfuClient.getManagementView().removeNode(
                server0.getEndpoint(),
                clientFixture.getNumRetry(),
                clientFixture.getTimeout(),
                clientFixture.getPollPeriod()
        );

        // Verify layout contains only the nodes that is not removed
        corfuClient.invalidateLayout();
        assertThat(corfuClient.getLayout().getAllServers())
                .doesNotContain(server0.getEndpoint());

        log.info("Second Verification:: Verify the data after detach the node");
        UfoUtils.verifyTableData(corfuStore, 0, count, namespace, tableName, false);
        log.info("Second Verification:: There is data change after detached the node");

        log.info("Update the records");
        UfoUtils.generateDataAndCommit(60, 90, tableName, uuids, events, tx, metadata, true);

        //Add corfu node back to the cluster
        corfuClient.getManagementView().addNode(
                server0.getEndpoint(),
                clientFixture.getNumRetry(),
                clientFixture.getTimeout(),
                clientFixture.getPollPeriod()
        );

        log.info("After rejoin the detached node, insert the data into the table");
        UfoUtils.generateDataAndCommit(100, 200, tableName, uuids, events, tx, metadata, false);

        // Verify layout should contain all three nodes
        corfuClient.invalidateLayout();
        assertThat(corfuClient.getLayout().getAllServers().size())
                .isEqualTo(corfuCluster.nodes().size());

        log.info("Verify cluster status is stable");
        waitForClusterStatusStable(corfuClient);

        log.info("Third Verification:: verify the table rows count after insertion of 100 rows");
        UfoUtils.verifyTableRowCount(corfuStore, namespace, tableName, count * 2);
        log.info("Third Verification:: table has 200 entries as expected");

        log.info("Third Verification:: Verify the data");
        UfoUtils.verifyTableData(corfuStore, 0, 60, namespace, tableName, false);
        UfoUtils.verifyTableData(corfuStore, 91, count * 2, namespace, tableName, false);

        log.info("Third Verification:: Verify the updated data");
        UfoUtils.verifyTableData(corfuStore, 60, 90, namespace, tableName, true);
        log.info("Third Verification:: Completed");

        UfoUtils.clearTableAndVerify(table, tableName, q);
    }
}
