package org.corfudb.universe.infrastructure.docker.universe.group.cluster;

import com.spotify.docker.client.DockerClient;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.universe.api.deployment.docker.DockerContainerParams;
import org.corfudb.universe.api.universe.group.Group.GroupParams.GenericGroupParams;
import org.corfudb.universe.api.universe.group.cluster.AbstractCluster;
import org.corfudb.universe.universe.group.cluster.corfu.CorfuCluster;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.universe.node.server.prometheus.PrometheusServerParams;
import org.corfudb.universe.infrastructure.docker.universe.node.server.prometheus.DockerPrometheusServer;
import org.corfudb.universe.infrastructure.docker.DockerManager;

/**
 * Provides Docker implementation of {@link CorfuCluster}.
 */
@Slf4j
public class DockerPrometheusCluster extends AbstractCluster<
        PrometheusServerParams,
        DockerContainerParams<PrometheusServerParams>,
        DockerPrometheusServer, 
        GenericGroupParams<PrometheusServerParams, DockerContainerParams<PrometheusServerParams>>> {

    @NonNull
    private final DockerClient docker;

    /**
     * Support cluster
     *
     * @param docker         docker client
     * @param supportParams  support params
     * @param universeParams universe params
     */
    @Builder
    public DockerPrometheusCluster(
            DockerClient docker, UniverseParams universeParams,
            GenericGroupParams<PrometheusServerParams, DockerContainerParams<PrometheusServerParams>> supportParams) {
        super(supportParams, universeParams);
        this.docker = docker;
    }

    @Override
    public void bootstrap() {
        // NOOP
    }

    @Override
    protected DockerPrometheusServer buildServer(DockerContainerParams<PrometheusServerParams> deploymentParams) {

        DockerManager<PrometheusServerParams> dockerManager = DockerManager
                .<PrometheusServerParams>builder()
                .docker(docker)
                .containerParams(deploymentParams)
                .build();

        return DockerPrometheusServer.builder()
                .containerParams(deploymentParams)
                .clusterParams(params)
                .params(deploymentParams.getApplicationParams())
                .docker(docker)
                .dockerManager(dockerManager)
                .build();
    }
}
