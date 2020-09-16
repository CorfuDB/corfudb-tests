package org.corfudb.universe.node.server.params;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import org.corfudb.universe.node.Node;
import org.corfudb.universe.node.Node.NodeParams;
import org.corfudb.universe.node.server.ServerUtil;

import java.util.Set;

@Builder
@EqualsAndHashCode
@ToString
@Getter
public class MangleServerParams implements NodeParams {
    private static final String DOCKER_IMAGE_NAME = "mangleuser/mangle";

    @NonNull
    @Default
    private final String dockerImage = DOCKER_IMAGE_NAME;

    @NonNull
    @Default
    private final String serverVersion = "2.0";

    @Default
    private final boolean enabled = false;

    @Default
    private final int port = ServerUtil.getRandomOpenPort();

    public String getDockerImageNameFullName() {
        return dockerImage + ":" + serverVersion;
    }

    @Override
    public String getName() {
        return "mangle-server-" + port;
    }

    @Override
    public Set<Integer> getPorts() {
        return null;
    }

    @Override
    public Node.NodeType getNodeType() {
        return null;
    }
}
