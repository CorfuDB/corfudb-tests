package org.corfudb.universe.api.workflow;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.corfudb.universe.api.universe.Universe;
import org.corfudb.universe.scenario.fixture.Fixture;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Manages a testing workflow lifecycle.
 * Provides api to:
 * - customize the universe parameters
 * - initialize and deploy the clusters in the universe
 * - get the universe fixture to setup the initial parameters
 * - shutdown the universe
 *
 * @param <F> universe fixture
 */
public interface UniverseWorkflow<P, F extends Fixture<P>> {
    WorkflowContext<P, F> getContext();

    UniverseWorkflow<P, F> initUniverse();

    /**
     * Set up universe fixtures
     *
     * @return universe workflow
     */
    UniverseWorkflow<P, F> init();

    default UniverseWorkflow<P, F> setup(Consumer<F> fixtureManager) {
        fixtureManager.accept(getContext().getFixture());
        return this;
    }

    /**
     * Deploying corfu universe
     *
     * @return universe workflow
     */
    default UniverseWorkflow<P, F> deployUniverse() {
        getContext()
                .universe
                .orElseThrow(() -> new IllegalStateException("Universe is not initialized"))
                .deploy();
        return this;
    }

    /**
     * Deploy corfu universe
     *
     * @return universe workflow
     */
    default UniverseWorkflow<P, F> deploy() {
        initUniverse();
        deployUniverse();
        return this;
    }

    /**
     * Provide universe fixture
     *
     * @return fixture
     */
    default F getFixture() {
        return getContext().getFixture();
    }

    /**
     * Returns corfu universe
     *
     * @return corfu universe
     */
    default Universe getUniverse() {
        return getContext()
                .getUniverse()
                .orElseThrow(() -> new IllegalStateException("Universe is not ready"));
    }

    @Builder
    @Getter
    class WorkflowConfig {
        @NonNull
        private final String corfuServerVersion;

        @NonNull
        private final String testName;
    }

    @Builder
    class WorkflowContext<P, F extends Fixture<P>> {
        @NonNull
        @Getter
        private final F fixture;

        @NonNull
        @Getter
        private final WorkflowConfig config;

        @Getter
        @Setter
        @Builder.Default
        private Optional<Universe> universe = Optional.empty();

        @Getter
        @Setter
        private boolean initialized;
    }
}
