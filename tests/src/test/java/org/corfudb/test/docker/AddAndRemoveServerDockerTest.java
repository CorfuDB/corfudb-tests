package org.corfudb.test.docker;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.test.TestGroups;
import org.corfudb.test.spec.AddAndRemoveServerSpec;
import org.corfudb.test.spec.AllNodesRestartServiceSpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Slf4j
@Tag(TestGroups.BAT_DOCKER)
public class AddAndRemoveServerDockerTest extends AbstractCorfuUniverseTest {

    private final AddAndRemoveServerSpec test = new AddAndRemoveServerSpec();

    @Test
    public void test() {
        testRunner.executeDockerTest(test::verifyAddAndRemoveNode);
    }
}
