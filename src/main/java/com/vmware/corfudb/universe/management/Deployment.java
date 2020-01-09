package com.vmware.corfudb.universe.management;

import com.vmware.corfudb.universe.UniverseConfigurator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Deployment {

    private final UniverseConfigurator configurator = UniverseConfigurator.builder().build();

    /**
     * Deploying a corfu cluster:
     * - disable shutdown logic to prevent the universe stop and clean corfu servers
     * - deploy corfu server
     */
    public static void main(String[] args) {
        log.info("Deploying corfu cluster...");

        Deployment deployment = new Deployment();
        deployment.deploy();

        log.info("Corfu cluster has deployed");

        System.exit(0);
    }

    public Deployment deploy() {
        configurator.universeManager.workflow(wf -> {
            wf.setupVm(configurator.vmSetup);
            //disable shutdown logic
            wf.setupVm(fixture -> fixture.getUniverse().cleanUpEnabled(false));
            wf.deploy();
        });

        return this;
    }
}
