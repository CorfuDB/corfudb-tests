package org.corfu.cloud.infrastructure.logstash;

import org.corfu.cloud.infrastructure.logstash.LogstashConfig.DockerVolume;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.JsonNode;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.SerializationFeature;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class CorfuTest {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final CorfuConfig corfuConfig = new CorfuConfig();

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(log);

    @Test
    public void test() throws Exception {

        Files.deleteIfExists(corfuConfig.outputFile);
        corfuConfig.outputDir.toFile().mkdirs();

        try (GenericContainer<?> logstash = new GenericContainer<>(corfuConfig.logstashCfg.logstashImage)) {

            resourceMapping(logstash, corfuConfig.logstashCfg.patterns());
            resourceMapping(logstash, corfuConfig.logstashCfg.templates());
            resourceMapping(logstash, corfuConfig.logstashCfg.logstashConf());
            resourceMapping(logstash, corfuConfig.logstashCfg.logstashYml());
            resourceMapping(logstash, corfuConfig.corfuConf);
            resourceMapping(logstash, corfuConfig.inputLog);

            logstash
                    .withEnv("SERVER", "127.0.0.1")
                    //logstash output
                    .withFileSystemBind("build/test-output", "logstash-test-output", BindMode.READ_WRITE)
                    .withCommand("/bin/sh", "-c", "logstash < " + corfuConfig.logFile)

                    .waitingFor(Wait.forLogMessage(".*Logstash shut down.*", 1))
                    .withStartupTimeout(Duration.ofMinutes(3));
            try {
                logstash.start();
                logstash.followOutput(logConsumer);
            } catch (Exception ex) {
                fail("Test failure");
            }

            Integer exitCode = logstash.getCurrentContainerInfo().getState().getExitCode();
            assertEquals(0, exitCode);

            //compare output with expected result
            List<String> output = Files.readAllLines(corfuConfig.outputFile);
            URI expectedOutputUri = getClass().getClassLoader().getResource("corfu/output.log").toURI();
            List<String> expectedOutput = Files.readAllLines(Paths.get(expectedOutputUri));

            for (int i = 0; i < expectedOutput.size(); i++) {
                String expectedLine = expectedOutput.get(i);
                String actualLine = output.get(i);

                JsonNode expectedJson = mapper.readTree(expectedLine);
                JsonNode actualJson = mapper.readTree(actualLine);

                JsonNode expectedMsg = expectedJson.get("msg");
                JsonNode actualMsg = actualJson.get("msg");

                assertThat(expectedMsg).isEqualTo(actualMsg);
            }
        }
    }

    private void resourceMapping(GenericContainer<?> logstash, DockerVolume vol) {
        logstash.withClasspathResourceMapping(
                vol.resourcePath().toString(), vol.containerPath().toString(), vol.mode()
        );
    }

    public static class CorfuConfig {
        private final LogstashConfig logstashCfg = ImmutableLogstashConfig.builder().build();

        private final Path outputDir = Paths.get("build", "test-output");
        private final Path outputFile = outputDir.resolve("output.log");

        private final DockerVolume corfuConf = ImmutableDockerVolume.of(
                Paths.get("pipeline/corfu.conf"),
                logstashCfg.pipeline().containerPath().resolve("corfu.conf"),
                BindMode.READ_ONLY
        );

        private final String logFile = "/var/log/corfu/corfu.9000.log";

        private final DockerVolume inputLog = ImmutableDockerVolume.of(
                Paths.get("corfu/input.log"),
                Paths.get(logFile),
                BindMode.READ_ONLY
        );
    }
}
