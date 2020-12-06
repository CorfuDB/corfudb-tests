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
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class CorfuJvmTest {
    private static final Logger logger = LoggerFactory.getLogger(CorfuJvmTest.class);

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final CorfuJvmConfig jvmConfig = new CorfuJvmConfig();

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(log);

    @Test
    public void test() throws Exception {

        Files.deleteIfExists(jvmConfig.outputFile);
        Files.createDirectories(
                jvmConfig.outputDir,
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxrwxrwx"))
        );
        logger.info("Output dir created: {}", jvmConfig.outputDir);

        try (GenericContainer<?> logstash = new GenericContainer<>(jvmConfig.logstashCfg.logstashImage)) {

            resourceMapping(logstash, jvmConfig.logstashCfg.patterns());
            resourceMapping(logstash, jvmConfig.logstashCfg.templates());
            resourceMapping(logstash, jvmConfig.logstashCfg.logstashConf());
            resourceMapping(logstash, jvmConfig.logstashCfg.logstashYml());
            resourceMapping(logstash, jvmConfig.corfuJvmConf);
            resourceMapping(logstash, jvmConfig.inputLog);

            logstash
                    .withEnv("SERVER", "127.0.0.1")
                    //logstash output
                    .withFileSystemBind(jvmConfig.outputDir.toString(), "logstash-test-output", BindMode.READ_WRITE)
                    .withCommand("/bin/sh", "-c", "logstash < " + jvmConfig.jvmGcLog)

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
            List<String> output = Files.readAllLines(jvmConfig.outputFile);
            URI expectedOutputUri = getClass().getClassLoader().getResource("corfu-jvm/output.log").toURI();
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

    public static class CorfuJvmConfig {
        private final LogstashConfig logstashCfg = ImmutableLogstashConfig.builder().build();

        private final Path outputDir = Paths.get("build", "test-output");
        private final Path outputFile = outputDir.resolve("output.log");

        private final DockerVolume corfuJvmConf = ImmutableDockerVolume.of(
                Paths.get("pipeline/corfu-jvm.conf"),
                logstashCfg.pipeline().containerPath().resolve("corfu-jvm.conf"),
                BindMode.READ_ONLY
        );

        private final String jvmGcLog = "/var/log/corfu/jvm/corfu.jvm.gc.9000.log.0";

        private final DockerVolume inputLog = ImmutableDockerVolume.of(
                Paths.get("corfu-jvm/input.log"),
                Paths.get(jvmGcLog),
                BindMode.READ_ONLY
        );
    }
}
