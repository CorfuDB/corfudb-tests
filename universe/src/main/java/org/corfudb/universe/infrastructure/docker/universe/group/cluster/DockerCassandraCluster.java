package org.corfudb.universe.infrastructure.docker.universe.group.cluster;

import com.spotify.docker.client.DockerClient;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.group.GroupParams.GenericGroupParams;
import org.corfudb.universe.api.universe.group.cluster.AbstractCluster;
import org.corfudb.universe.infrastructure.docker.DockerManager;
import org.corfudb.universe.infrastructure.docker.universe.node.server.DockerNode;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster;
import org.corfudb.universe.universe.node.server.cassandra.CassandraServerParams;

/**
 * Provides Docker implementation of {@link CorfuCluster}.
 */
@Slf4j
public class DockerCassandraCluster extends AbstractCluster<
        CassandraServerParams,
        DockerContainerParams<CassandraServerParams>,
        DockerNode<CassandraServerParams>,
        GenericGroupParams<CassandraServerParams, DockerContainerParams<CassandraServerParams>>> {

    @NonNull
    private final DockerClient docker;

    /**
     * Support cluster
     *
     * @param docker          docker client
     * @param cassandraParams cassandra server params
     * @param universeParams  universe params
     */
    @Builder
    public DockerCassandraCluster(
            DockerClient docker, UniverseParams universeParams,
            GenericGroupParams<CassandraServerParams, DockerContainerParams<CassandraServerParams>> cassandraParams) {
        super(cassandraParams, universeParams);
        this.docker = docker;
        init();
    }

    @Override
    public void bootstrap() {
        // NOOP
    }

    @Override
    protected DockerNode<CassandraServerParams> buildServer(
            DockerContainerParams<CassandraServerParams> deploymentParams) {

        DockerManager<CassandraServerParams> dockerManager = DockerManager
                .<CassandraServerParams>builder()
                .docker(docker)
                .containerParams(deploymentParams)
                .build();

        return DockerNode.<CassandraServerParams>builder()
                .containerParams(deploymentParams)
                .groupParams(params)
                .appParams(deploymentParams.getApplicationParams())
                .docker(docker)
                .dockerManager(dockerManager)
                .build();
    }
}