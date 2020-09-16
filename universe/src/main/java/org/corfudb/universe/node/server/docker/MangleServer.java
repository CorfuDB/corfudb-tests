package org.corfudb.universe.node.server.docker;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.ListImagesParam;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.Image;
import com.spotify.docker.client.messages.PortBinding;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.corfudb.universe.node.Node;
import org.corfudb.universe.node.NodeException;
import org.corfudb.universe.node.server.params.MangleServerParams;
import org.corfudb.universe.universe.UniverseParams;
import org.corfudb.universe.util.DockerManager;
import org.corfudb.universe.util.IpAddress;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Builder
public class MangleServer<U extends UniverseParams> implements Node {
    private static final String ALL_NETWORK_INTERFACES = "0.0.0.0";

    @NonNull
    private final DockerClient docker;

    @NonNull
    private final DockerManager dockerManager;

    @NonNull
    private final MangleServerParams params;

    @NonNull
    protected final U universeParams;

    private final AtomicReference<IpAddress> ipAddress = new AtomicReference<>();

    @Override
    public Node deploy() {
        return null;
    }

    @Override
    public void stop(Duration timeout) {

    }

    @Override
    public void kill() {

    }

    @Override
    public void destroy() {

    }

    @Override
    public NodeParams getParams() {
        return params;
    }

    private String deployContainer() {
        ContainerConfig containerConfig = buildContainerConfig();

        String id;
        try {
            ListImagesParam corfuImageQuery = ListImagesParam
                    .byName(params.getDockerImageNameFullName());

            List<Image> corfuImages = docker.listImages(corfuImageQuery);
            if (corfuImages.isEmpty()) {
                docker.pull(params.getDockerImageNameFullName());
            }

            ContainerCreation container = docker.createContainer(containerConfig, params.getName());
            id = container.id();

            dockerManager.addShutdownHook(params.getName());

            docker.disconnectFromNetwork(id, "bridge");
            docker.connectToNetwork(id, docker.inspectNetwork(universeParams.getNetworkName()).id());

            dockerManager.start(id);

            String ipAddr = docker.inspectContainer(id)
                    .networkSettings()
                    .networks()
                    .values()
                    .asList()
                    .get(0)
                    .ipAddress();

            if (StringUtils.isEmpty(ipAddr)) {
                throw new NodeException("Empty Ip address for container: " + params.getName());
            }

            ipAddress.set(IpAddress.builder().ip(ipAddr).build());
        } catch (InterruptedException | DockerException e) {
            throw new NodeException("Can't start a container", e);
        }

        return id;
    }

    private ContainerConfig buildContainerConfig() {
        // Bind ports
        List<String> ports = params.getPorts().stream()
                .map(Objects::toString)
                .collect(Collectors.toList());
        Map<String, List<PortBinding>> portBindings = new HashMap<>();
        for (String port : ports) {
            List<PortBinding> hostPorts = new ArrayList<>();
            hostPorts.add(PortBinding.of(ALL_NETWORK_INTERFACES, port));
            portBindings.put(port, hostPorts);
        }

        HostConfig.Builder hostConfigBuilder = HostConfig.builder();

        HostConfig hostConfig = hostConfigBuilder
                .privileged(true)
                .portBindings(portBindings)
                .build();

        // Compose command line for starting Corfu
        String cmdLine = "ls";

        return ContainerConfig.builder()
                .hostConfig(hostConfig)
                .image(params.getDockerImageNameFullName())
                .hostname(params.getName())
                .exposedPorts(ports.toArray(new String[0]))
                .cmd("sh", "-c", cmdLine)
                .build();
    }
}
