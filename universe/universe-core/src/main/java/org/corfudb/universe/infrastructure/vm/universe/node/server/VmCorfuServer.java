package org.corfudb.universe.infrastructure.vm.universe.node.server;

import com.google.common.collect.ImmutableSortedSet;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.universe.api.common.IpAddress;
import org.corfudb.universe.api.common.LoggingParams;
import org.corfudb.universe.api.deployment.vm.VmParams;
import org.corfudb.universe.api.deployment.vm.VmParams.VsphereParams;
import org.corfudb.universe.api.universe.UniverseParams;
import org.corfudb.universe.api.universe.node.ApplicationServer;
import org.corfudb.universe.api.universe.node.ApplicationServers.CorfuApplicationServer;
import org.corfudb.universe.api.universe.node.NodeException;
import org.corfudb.universe.infrastructure.process.universe.node.server.CorfuProcessManager;
import org.corfudb.universe.infrastructure.process.universe.node.server.CorfuServerPath;
import org.corfudb.universe.infrastructure.vm.universe.VmManager;
import org.corfudb.universe.infrastructure.vm.universe.group.cluster.RemoteOperationHelper;
import org.corfudb.universe.infrastructure.vm.universe.node.stress.VmStress;
import org.corfudb.universe.universe.node.client.LocalCorfuClient;
import org.corfudb.universe.universe.node.server.corfu.CorfuServerParams;
import org.corfudb.universe.util.IpTablesUtil;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Implements a {@link ApplicationServer} instance that is running on VM.
 */
@Slf4j
public class VmCorfuServer implements CorfuApplicationServer {

    @NonNull
    @Getter
    private final VmManager vmManager;

    @NonNull
    private final IpAddress ipAddress;

    @Getter
    @NonNull
    private final RemoteOperationHelper remoteOperationHelper;

    @NonNull
    private final VmStress stress;

    @NonNull
    private final CorfuProcessManager processManager;

    @NonNull
    private final CorfuServerPath serverPath;

    @NonNull
    private final VmParams<CorfuServerParams> deploymentParams;

    @NonNull
    private final VsphereParams vsphereParams;

    @NonNull
    private final LoggingParams loggingParams;

    /**
     * VmCorfuServer constructor
     *
     * @param vmManager             vm manager
     * @param universeParams        universe params
     * @param stress                stress
     * @param remoteOperationHelper remote operation helper
     * @param loggingParams         logging params
     */
    @Builder
    public VmCorfuServer(
            VmParams<CorfuServerParams> deploymentParams, VmManager vmManager, UniverseParams universeParams,
            VmStress stress, RemoteOperationHelper remoteOperationHelper, LoggingParams loggingParams,
            VsphereParams vsphereParams) {
        this.deploymentParams = deploymentParams;
        this.vsphereParams = vsphereParams;
        this.vmManager = vmManager;
        this.ipAddress = getIpAddress();
        this.stress = stress;
        this.remoteOperationHelper = remoteOperationHelper;
        this.serverPath = new CorfuServerPath(deploymentParams.getApplicationParams());
        this.processManager = new CorfuProcessManager(serverPath, deploymentParams.getApplicationParams());
        this.loggingParams = loggingParams;
    }

    /**
     * Deploys a Corfu server on the VM as specified, including the following steps:
     * a) Copy the corfu jar file under the working directory to the VM
     * b) Run that jar file using java on the VM
     */
    @Override
    public void deploy() {
        log.info("Deploy vm server: {}", deploymentParams.getVmName());

        executeCommand(processManager.createServerDirCommand());
        executeCommand(processManager.createStreamLogDirCommand());

        remoteOperationHelper.copyFile(
                getParams().getInfrastructureJar(),
                serverPath.getServerJar()
        );

        start();
    }

    /**
     * Symmetrically disconnect a server from a list of other servers,
     * which creates a partial partition.
     *
     * @param servers List of servers to disconnect from
     */
    @Override
    public void disconnect(List<ApplicationServer<CorfuServerParams>> servers) {
        log.info("Disconnecting the VM server: {} from the specified servers: {}",
                getParams().getName(), servers);

        servers.stream()
                .filter(s -> !s.getParams().equals(getParams()))
                .forEach(s -> {
                    executeSudoCommand(String.join(" ", IpTablesUtil.dropInput(s.getIpAddress())));
                    executeSudoCommand(String.join(" ", IpTablesUtil.dropOutput(s.getIpAddress())));
                });
    }

    /**
     * Pause the {@link ApplicationServer} process on the VM
     */
    @Override
    public void pause() {
        log.info("Pausing the VM Corfu server: {}", getParams().getName());

        executeCommand(processManager.pauseCommand());
    }

    /**
     * Start a {@link ApplicationServer} process on the VM
     */
    @Override
    public void start() {
        // Compose command line for starting Corfu
        String cmdLine = getParams().buildCorfuArguments(getNetworkInterface());

        String cmd = String.format(
                "sh -c '%s'",
                processManager.startCommand(cmdLine)
        );
        executeCommand(cmd);
    }

    /**
     * Restart the {@link ApplicationServer} process on the VM
     */
    @Override
    public void restart() {
        stop(getParams().getCommonParams().getStopTimeout());
        start();
    }

    /**
     * Reconnect a server to the cluster
     */
    @Override
    public void reconnect() {
        log.info("Reconnecting the VM server: {} to the cluster.", deploymentParams.getVmName());

        executeSudoCommand(String.join(" ", IpTablesUtil.cleanInput()));
        executeSudoCommand(String.join(" ", IpTablesUtil.cleanOutput()));
    }

    /**
     * Reconnect a server to a list of servers.
     *
     * @param servers list of corfu servers
     */
    @Override
    public void reconnect(List<ApplicationServer<CorfuServerParams>> servers) {
        log.info("Reconnecting the VM server: {} to specified servers: {}",
                getParams().getName(), servers);

        servers.stream()
                .filter(s -> !s.getParams().equals(getParams()))
                .forEach(s -> {
                    executeSudoCommand(String.join(" ", IpTablesUtil.revertDropInput(s.getIpAddress())));
                    executeSudoCommand(String.join(" ", IpTablesUtil.revertDropOutput(s.getIpAddress())));
                });
    }

    /**
     * Execute a shell command in a vm
     *
     * @param command a shell command
     * @return command output
     */
    @Override
    public String execute(String command) {
        return executeCommand(command);
    }

    /**
     * Resume a {@link ApplicationServer}
     */
    @Override
    public void resume() {
        log.info("Resuming the corfu server: {}", getParams().getName());
        executeCommand(processManager.resumeCommand());
    }

    @Override
    public boolean isRunning() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Executes a certain command on the VM.
     */
    private String executeCommand(String cmdLine) {
        return remoteOperationHelper.executeCommand(cmdLine);
    }

    /**
     * Executes a certain Sudo command on the VM.
     */
    private String executeSudoCommand(String cmdLine) {
        return remoteOperationHelper.executeSudoCommand(cmdLine);
    }

    /**
     * Get vm ip address
     *
     * @return the IpAddress of this VM.
     */
    @Override
    public IpAddress getIpAddress() {
        return vmManager.getIpAddress();
    }

    /**
     * Stop corfu server
     *
     * @param timeout a limit within which the method attempts to gracefully stop the {@link ApplicationServer}.
     */
    @Override
    public void stop(Duration timeout) {
        log.info("Stop corfu server on vm: {}, params: {}", deploymentParams.getVmName(), getParams());

        try {
            executeCommand(processManager.stopCommand());
        } catch (Exception e) {
            String err = String.format("Can't STOP corfu: %s. Process not found on vm: %s, ip: %s",
                    getParams().getName(), deploymentParams.getVmName(), ipAddress
            );
            throw new NodeException(err, e);
        }
    }

    /**
     * Kill the {@link ApplicationServer} process on the VM directly.
     */
    @Override
    public void kill() {
        log.info("Kill the corfu server. Params: {}", getParams());
        try {
            executeCommand(processManager.killCommand());
        } catch (Exception e) {
            String err = String.format("Can't KILL corfu: %s. Process not found on vm: %s, ip: %s",
                    getParams().getName(), deploymentParams.getVmName(), ipAddress
            );
            throw new NodeException(err, e);
        }
    }

    /**
     * Destroy the {@link ApplicationServer} by killing the process and removing the files
     *
     * @throws NodeException this exception will be thrown if the server can not be destroyed.
     */
    @Override
    public void destroy() {
        log.info("Destroy node: {}", getParams().getName());
        kill();
        try {
            executeSudoCommand(IpTablesUtil.cleanAll());
            collectLogs();
            removeAppDir();
        } catch (Exception e) {
            throw new NodeException("Can't clean corfu directories", e);
        }
    }

    @Override
    public CorfuServerParams getParams() {
        return deploymentParams.getApplicationParams();
    }

    /**
     * Remove corfu server application dir.
     * AppDir is a directory that contains corfu-infrastructure jar file and could have log files, stream-log files and
     * so on, whatever used by the application.
     */
    private void removeAppDir() {
        executeCommand(processManager.removeServerDirCommand());
    }

    @Override
    public IpAddress getNetworkInterface() {
        return ipAddress;
    }

    @Override
    public void collectLogs() {
        if (!loggingParams.isEnabled()) {
            log.debug("Logging is disabled");
            return;
        }

        log.info("Download corfu server logs: {}", getParams().getName());

        Path corfuLogDir = getLogDir();

        try {
            remoteOperationHelper.downloadFile(
                    corfuLogDir.resolve(getParams().getName() + ".log"),
                    serverPath.getCorfuLogFile()
            );
        } catch (Exception e) {
            log.error("Can't download logs for corfu server: {}", getParams().getName(), e);
        }
    }

    @Override
    public Path getLogDir() {
        Path corfuLogDir = getParams()
                .getCommonParams()
                .getUniverseDirectory()
                .resolve("logs")
                .resolve(loggingParams.getRelativeServerLogDir());

        File logDirFile = corfuLogDir.toFile();
        if (!logDirFile.exists() && logDirFile.mkdirs()) {
            log.info("Created new corfu log directory at {}.", corfuLogDir);
        }
        return corfuLogDir;
    }

    @Override
    public LocalCorfuClient getLocalCorfuClient() {
        LocalCorfuClient localClient = LocalCorfuClient.builder()
                .serverEndpoints(ImmutableSortedSet.of(getEndpoint()))
                .corfuRuntimeParams(CorfuRuntime.CorfuRuntimeParameters.builder())
                .build();

        localClient.deploy();
        return localClient;
    }
}
