package org.corfudb.test.docker;

import lombok.Builder;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.corfudb.runtime.collections.CorfuTable;
import org.corfudb.runtime.view.Layout;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.universe.UniverseManager.UniverseWorkflow;
import org.corfudb.universe.group.cluster.CorfuCluster;
import org.corfudb.universe.node.client.CorfuClient;
import org.corfudb.universe.node.server.CorfuServer;
import org.corfudb.universe.scenario.fixture.Fixture;
import org.corfudb.universe.universe.UniverseParams;
import org.corfudb.util.Sleep;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.corfudb.universe.scenario.fixture.Fixtures.TestFixtureConst.DEFAULT_STREAM_NAME;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForLayoutChange;
import static org.corfudb.universe.test.util.ScenarioUtils.waitForUnresponsiveServersChange;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@Tag(TestGroups.BAT_DOCKER)
public class FileDescriptorLeaksTest extends AbstractCorfuUniverseTest {

    @Test
    public void test() {
        testRunner.executeDockerTest(this::verifyAddAndRemoveNode);
    }

    public void verifyAddAndRemoveNode(UniverseWorkflow<Fixture<UniverseParams>> wf) throws Exception {

        String groupName = wf.getFixture().data().getGroupParamByIndex(0).getName();
        CorfuCluster corfuCluster = wf.getUniverse().getGroup(groupName);

        CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

        CorfuTable<String, String> table = corfuClient
                .createDefaultCorfuTable(DEFAULT_STREAM_NAME);

        long start = System.currentTimeMillis();

        String payload = RandomStringUtils.randomAlphanumeric(50_000);

        for (int i = 0; i < 30_001; i++) {
            //System.out.println("next iteration: " + i);
            table.put(String.valueOf(i), payload);
            long end = System.currentTimeMillis();
            if (i % 1000 == 0) {
                System.out.println("took: " + Duration.ofMillis(end - start) + ", iteration: " + i);
            }
        }

        long end = System.currentTimeMillis();
        System.out.println("!!!!!!!!!!! took: " + Duration.ofMillis(end - start));

        CorfuServer server0 = corfuCluster.getServerByIndex(0);
        CorfuServer server1 = corfuCluster.getServerByIndex(1);
        CorfuServer server2 = corfuCluster.getServerByIndex(2);

        CompletableFuture<Void> cf = CompletableFuture.runAsync(()->{
            int counter = 30_002;
            while (true) {
                table.put(String.valueOf(counter), payload);
                table.get(new Random().nextInt(counter));
                counter++;
                Sleep.sleepUninterruptibly(Duration.ofMillis(500));
            }
        });

        for (int i = 0; i < 50; i++) {
            System.out.println("Disconnect server0 with server1 and server2");
            server0.disconnect(Arrays.asList(server1, server2));
            waitForLayoutChange(layout -> layout.getUnresponsiveServers()
                    .equals(Collections.singletonList(server0.getEndpoint())), corfuClient);

            TimeUnit.SECONDS.sleep(3);
            System.out.println("reconnect. iteration: " + i + ", sleep 30 sec");
            server0.reconnect();

            waitForUnresponsiveServersChange(size -> size == 0, corfuClient);

            TimeUnit.SECONDS.sleep(1);

            Layout layout = corfuClient.getLayout();
            if (!layout.getUnresponsiveServers().isEmpty()) {
                fail("yay: " + layout.getUnresponsiveServers());
            }

            layout = corfuClient.getLayout();
            System.out.println("timeout some sec. Unresponsive: "+ layout.getUnresponsiveServers() + ", Layout: " + corfuClient.getLayout());
            TimeUnit.SECONDS.sleep(new Random().nextInt(20));

            check(server0, wf);
        }

        cf.cancel(true);
        wf.shutdown();
    }

    public void check(CorfuServer server, UniverseWorkflow<Fixture<UniverseParams>> wf) {
        String[] lsofOutput = server.execute("lsof").split("\\r?\\n");
        List<LsofRecord> lsOfList = Arrays
                .stream(lsofOutput)
                .map(LsofRecord::parse)
                .collect(Collectors.toList());

        for (LsofRecord record : lsOfList) {
            boolean isDeleted = record.state == FileState.DELETED;
            boolean isCorfuLogFile = record.path.startsWith("/app/" + server.getParams().getName() + "/db/corfu/log");

            if (isCorfuLogFile && isDeleted) {
                log.error("File descriptor leaks has been detected: {}", record);
                wf.shutdown();
                fail("File descriptor leaks has been detected: " + record);
            }
        }
    }

    @ToString
    @Builder
    private static class LsofRecord {
        private final int numberOfResources;
        private final String process;
        private final String path;
        @Builder.Default
        private final FileState state = FileState.NA;

        public static LsofRecord parse(String rawLsof) {
            String[] components = rawLsof.split("\t");

            String path = components[2];
            String[] pathAndState = path.split(" ");

            FileState state = FileState.OPEN;
            if (pathAndState.length > 1) {
                String stateStr = pathAndState[pathAndState.length - 1].trim();
                if (stateStr.equals("(deleted)")) {
                    state = FileState.DELETED;
                }
            }

            return LsofRecord.builder()
                    .numberOfResources(Integer.parseInt(components[0]))
                    .process(components[1])
                    .path(path)
                    .state(state)
                    .build();
        }
    }

    enum FileState {
        DELETED, OPEN, NA
    }
}
