package org.corfudb.universe.api.universe.node;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSet;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import org.corfudb.universe.universe.node.server.ServerUtil;
import org.slf4j.event.Level;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Set;

/**
 * Common parameters for all nodes
 */
@Builder
@EqualsAndHashCode
@ToString
public class CommonNodeParams implements Comparable<CommonNodeParams> {

    @Builder.Default
    @Getter
    @NonNull
    private final Set<Integer> ports = ImmutableSet.of(ServerUtil.getRandomOpenPort());

    @Builder.Default
    @NonNull
    @Getter
    @EqualsAndHashCode.Exclude
    private final Level logLevel = Level.INFO;

    @NonNull
    @Getter
    private final Node.NodeType nodeType;

    @Getter
    @NonNull
    protected final String clusterName;

    @Getter
    @Builder.Default
    @NonNull
    @EqualsAndHashCode.Exclude
    private final Duration stopTimeout = Duration.ofSeconds(1);

    @Builder.Default
    @Getter
    private final boolean enabled = true;

    /**
     * The directory where the universe framework keeps files needed for the framework functionality.
     * By default the directory is equal to the build directory of a build tool
     * ('target' directory in case of maven, 'build' directory in case of gradle)
     */
    @Getter
    @NonNull
    @Builder.Default
    private final Path universeDirectory = Paths.get("target");

    /**
     * A name of the node
     *
     * @return node  name
     */
    public String getName() {
        return String.format(
                "%s-%s%d",
                clusterName, nodeType.name().toLowerCase(), ports.stream().findFirst().orElse(-1)
        );
    }

    /**
     * Compare two node parameters
     *
     * @param other another  node params
     * @return comparison result
     */
    @Override
    public int compareTo(CommonNodeParams other) {
        return ComparisonChain.start()
                .compare(this.getName(), other.getName())
                .compare(this.getNodeType(), other.getNodeType())
                .result();
    }
}
