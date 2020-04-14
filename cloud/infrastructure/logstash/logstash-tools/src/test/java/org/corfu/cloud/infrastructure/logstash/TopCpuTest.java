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

public class TopCpuTest {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final TopCpuConfig ioStatConfig = new TopCpuConfig();

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(log);

    @Test
    public void test() throws Exception {

        Files.deleteIfExists(ioStatConfig.outputFile);
        ioStatConfig.outputDir.toFile().mkdirs();

        try (GenericContainer<?> logstash = new GenericContainer<>(ioStatConfig.logstashCfg.logstashImage)) {

            resourceMapping(logstash, ioStatConfig.logstashCfg.patterns());
            resourceMapping(logstash, ioStatConfig.logstashCfg.templates());
            resourceMapping(logstash, ioStatConfig.logstashCfg.logstashConf());
            resourceMapping(logstash, ioStatConfig.logstashCfg.logstashYml());
            resourceMapping(logstash, ioStatConfig.ioStatConf);
            resourceMapping(logstash, ioStatConfig.inputLog);
            resourceMapping(logstash, ioStatConfig.parser);

            logstash
                    //logstash output
                    .withFileSystemBind("build/test-output", "/logstash-test-output", BindMode.READ_WRITE)
                    .withCommand("/bin/sh", "-c", "logstash < " + ioStatConfig.sysIoLog)

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
            List<String> output = Files.readAllLines(ioStatConfig.outputFile);
            URI expectedOutputUri = getClass().getClassLoader().getResource("top-cpu/output.log").toURI();
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

    public static class TopCpuConfig {
        private final LogstashConfig logstashCfg = ImmutableLogstashConfig.builder().build();

        private final Path outputDir = Paths.get("build", "test-output");
        private final Path outputFile = outputDir.resolve("output.log");

        private final DockerVolume ioStatConf = ImmutableDockerVolume.of(
                Paths.get("pipeline/top-cpu.conf"),
                logstashCfg.pipeline().containerPath().resolve("top-cpu.conf"),
                BindMode.READ_ONLY
        );

        private final String sysIoLog = "/var/log/vmware/top-cpu.log";

        private final DockerVolume inputLog = ImmutableDockerVolume.of(
                Paths.get("top-cpu/input.log"),
                Paths.get(sysIoLog),
                BindMode.READ_ONLY
        );

        private final String parserPath = "/tmp/parser.rb";

        private final DockerVolume parser = ImmutableDockerVolume.of(
                Paths.get("top-cpu/parser.rb"),
                Paths.get(parserPath),
                BindMode.READ_ONLY
        );
    }
}