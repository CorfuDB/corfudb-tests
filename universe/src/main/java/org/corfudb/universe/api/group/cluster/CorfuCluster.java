package org.corfudb.universe.api.group.cluster;

import org.corfudb.runtime.CorfuRuntime.CorfuRuntimeParameters.CorfuRuntimeParametersBuilder;
import org.corfudb.universe.api.deployment.DeploymentParams;
import org.corfudb.universe.api.group.Group.GroupParams;
import org.corfudb.universe.api.node.Node;
import org.corfudb.universe.api.node.NodeException;
import org.corfudb.universe.node.client.LocalCorfuClient;
import org.corfudb.universe.node.server.CorfuServer;

/**
 * Provides a Corfu specific cluster of servers
 */
public interface CorfuCluster<
        P extends Node.NodeParams,
        D  extends DeploymentParams<P>,
        T extends Node<P, T>,
        G extends GroupParams<P, D>
        > extends Cluster<P, D, T, G> {

    /**
     * Provides a corfu client running on local machine
     *
     * @return local corfu client
     */
    LocalCorfuClient getLocalCorfuClient();

    /**
     * Provides a corfu client running on local machine with the given metrics port open
     *
     * @param runtimeParametersBuilder rt builder
     * @return local corfu client
     */
    LocalCorfuClient getLocalCorfuClient(CorfuRuntimeParametersBuilder runtimeParametersBuilder);

    /**
     * Find a corfu server by index in the cluster:
     * - get all corfu servers in the cluster
     * - skip particular number of servers in the cluster according to index offset
     * - extract first server from the list
     *
     * @param index corfu server position
     * @return a corfu server
     */
    default CorfuServer getServerByIndex(int index) {
        return nodes()
                .values()
                .stream()
                .skip(index)
                .findFirst()
                .map(CorfuServer.class::cast)
                .orElseThrow(() -> new NodeException("Corfu server not found by index: " + index));
    }

    /**
     * To find the first corfu server in a cluster:
     * - get all corfu servers in the cluster
     * - extract first element in the list
     *
     * @return the first corfu server
     */
    default CorfuServer getFirstServer() {
        return getServerByIndex(0);
    }
}
