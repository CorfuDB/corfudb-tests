package org.corfudb.universe.test;

import lombok.Builder;
import lombok.Builder.Default;
import org.apache.commons.io.FilenameUtils;
import org.corfudb.universe.scenario.fixture.Fixtures.UniverseFixture;
import org.corfudb.universe.scenario.fixture.Fixtures.VmUniverseFixture;
import org.corfudb.universe.test.util.PropertiesLoader;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;

@Builder
public class UniverseConfigurator {

    private static final Path CFG = Paths.get("universe-tests.properties");

    @Default
    public final Consumer<VmUniverseFixture> vmSetup = fixture -> {
        Properties props = getCfg();

        fixture.getCluster().name(props.getProperty("corfu.cluster.name"));
        int port = Integer.parseInt(props.getProperty("corfu.server.initialPort"));
        fixture.getFixtureUtilBuilder().initialPort(Optional.of(port));

        Path jarDirectory = Paths.get(FilenameUtils.getName(props.getProperty("corfu.server.jar")));
        fixture.getServers().universeDirectory(jarDirectory);

        fixture.getLogging().enabled(true);
    };

    @Default
    public final Consumer<UniverseFixture> dockerSetup = fixture -> {
        Properties props = getCfg();

        fixture.getServer().dockerImage("corfudb/corfu-server");
        fixture.getCluster().serverVersion(getServerVersion());
        fixture.getLogging().enabled(true);

        Path universeDirectory = Paths.get(FilenameUtils.getName(props.getProperty("corfu.server.jar")));
        fixture.getServer().universeDirectory(universeDirectory);
    };

    public static String getServerVersion() {
        return getCfg().getProperty("server.version");
    }

    /**
     * Parse {@link UniverseConfigurator.CFG} config file
     *
     * @return universe tests configuration
     */
    public static Properties getCfg() {
        return new PropertiesLoader()
                .loadPropertiesFile(CFG)
                .get();
    }
}
