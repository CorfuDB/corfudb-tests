package org.corfudb.test.mangle;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.CorfuStore;
import org.corfudb.test.AbstractCorfuUniverseTest;
import org.corfudb.universe.group.Group.GroupParams;
import org.corfudb.universe.group.cluster.CorfuCluster;
import org.corfudb.universe.node.Node;
import org.corfudb.universe.node.Node.NodeParams;
import org.corfudb.universe.node.client.CorfuClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Slf4j
@Tag("yay")
public class MangleTest extends AbstractCorfuUniverseTest {

    @Test
    public void test(){
        testRunner.executeDockerTest(wf -> {
            CorfuCluster<Node, GroupParams<NodeParams>> corfuCluster = wf.getUniverse()
                    .getGroup(wf.getFixture().data().getGroupParamByIndex(0).getName());

            CorfuClient corfuClient = corfuCluster.getLocalCorfuClient();

            CorfuRuntime runtime = corfuClient.getRuntime();
            // Creating Corfu Store using a connected corfu client.
            CorfuStore corfuStore = new CorfuStore(runtime);

            //run mangle
        });
    }
}
