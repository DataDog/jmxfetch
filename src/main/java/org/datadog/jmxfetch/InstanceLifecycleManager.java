package org.datadog.jmxfetch;

import static org.datadog.jmxfetch.Instance.isDirectInstance;

import lombok.extern.slf4j.Slf4j;

import org.datadog.jmxfetch.reporter.Reporter;
import org.datadog.jmxfetch.tasks.TaskMethod;
import org.datadog.jmxfetch.tasks.TaskProcessor;
import org.datadog.jmxfetch.tasks.TaskStatusHandler;
import org.datadog.jmxfetch.util.AppTelemetry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unchecked")
@Slf4j
class InstanceLifecycleManager {

    private static final String RECOVERY_POOL_NAME = "jmxfetch-recoveryPool";

    private final AppConfig appConfig;
    private final TaskProcessor recoveryProcessor;
    private final App app;
    private final List<Instance> instances = new ArrayList<>();
    private final Map<String, Instance> brokenInstanceMap = new ConcurrentHashMap<>();

    InstanceLifecycleManager(
            final AppConfig appConfig,
            final TaskProcessor recoveryProcessor,
            final App app) {
        this.appConfig = appConfig;
        this.recoveryProcessor = recoveryProcessor;
        this.app = app;
    }

    List<Instance> getInstances() {
        return this.instances;
    }

    Map<String, Instance> getBrokenInstanceMap() {
        return this.brokenInstanceMap;
    }

    void clearInstances(final Collection<Instance> instances) {
        final List<InstanceTask<Void>> cleanupInstanceTasks =
                new ArrayList<>(instances.size());
        for (final Instance instance : instances) {
            // create the cleanup task
            cleanupInstanceTasks.add(new InstanceCleanupTask(instance));
        }

        try {
            if (!this.recoveryProcessor.ready()) {
                log.warn(
                        "Executor has to be replaced for recovery processor, "
                        + "previous one hogging threads");
                this.recoveryProcessor.stop();
                this.recoveryProcessor.setThreadPoolExecutor(
                        this.app.buildExecutorService(
                                this.appConfig.getReconnectionThreadPoolSize(),
                                RECOVERY_POOL_NAME));
            }

            List<TaskStatusHandler> statuses =
                    this.recoveryProcessor.processTasks(
                            cleanupInstanceTasks,
                            this.appConfig.getReconnectionTimeout(),
                            TimeUnit.SECONDS,
                            new TaskMethod<Void>() {
                                @Override
                                public TaskStatusHandler invoke(
                                        final Instance instance,
                                        final Future<Void> future,
                                        final Reporter reporter) {
                                    return App.processRecoveryResults(instance, future, reporter);
                                }
                            });

        } catch (Exception e) {
            log.warn(
                    "Unable to terminate all connections gracefully "
                    + "- possible network connectivity issues.");
        } finally {
            // This is a best effort thing, we always clear the list - eventually 'orphaned'
            // instances should get GC'd anyhow.
            instances.clear();
        }
    }

    void fixBrokenInstances(final Reporter reporter, final AppTelemetry appTelemetry) {
        if (this.brokenInstanceMap.isEmpty()) {
            return;
        }

        log.debug("Trying to recover broken instances...");
        final List<InstanceTask<Void>> fixInstanceTasks =
                new ArrayList<>(this.brokenInstanceMap.values().size());

        for (final Instance instance : this.brokenInstanceMap.values()) {
            // Clearing rates aggregator so we won't compute wrong rates if we can reconnect
            reporter.clearRatesAggregator(instance.getName());
            reporter.clearCountersAggregator(instance.getName());

            log.warn(
                    "Instance "
                            + instance
                            + " didn't return any metrics. "
                            + "Maybe the server got disconnected ? Trying to reconnect.");

            // Remove the broken instance from the good instance list so jmxfetch won't try to
            // collect metrics from this broken instance during next collection and close
            // ongoing connections (do so asynchronously to avoid locking on network timeout).
            instance.cleanUpAsync();
            this.instances.remove(instance);

            // Resetting the instance
            final Instance newInstance = new Instance(instance, this.appConfig);

            // create the initializing task
            fixInstanceTasks.add(new InstanceInitializingTask(newInstance, true));
        }

        try {
            if (!this.recoveryProcessor.ready()) {
                log.warn(
                        "Executor has to be replaced for recovery processor, "
                        + "previous one hogging threads");
                this.recoveryProcessor.stop();
                this.recoveryProcessor.setThreadPoolExecutor(
                        this.app.buildExecutorService(
                                this.appConfig.getReconnectionThreadPoolSize(),
                                RECOVERY_POOL_NAME));
            }

            Collections.shuffle(fixInstanceTasks);
            final List<TaskStatusHandler> statuses =
                    this.recoveryProcessor.processTasks(
                            fixInstanceTasks,
                            this.appConfig.getReconnectionTimeout(),
                            TimeUnit.SECONDS,
                            new TaskMethod<Void>() {
                                @Override
                                public TaskStatusHandler invoke(
                                        final Instance instance,
                                        final Future<Void> future,
                                        final Reporter reporter) {
                                    return App.processRecoveryResults(instance, future, reporter);
                                }
                            });

            this.app.processFixedStatus(fixInstanceTasks, statuses);

            // update with statuses
            this.app.processStatus(fixInstanceTasks, statuses);

        } catch (Exception e) {
            // NADA
        }

        log.debug("Done trying to recover broken instances.");
    }

    void init(
            final boolean forceNewConnection,
            final Map<String, ConfigYaml> configs,
            final Map<String, ConfigYaml> adPipeConfigs,
            final Map<String, Object> adJsonConfigs,
            final AppTelemetry appTelemetry) {
        log.info("Cleaning up instances...");
        this.clearInstances(this.instances);
        this.clearInstances(this.brokenInstanceMap.values());
        this.brokenInstanceMap.clear();

        final List<Instance> newInstances = new ArrayList<>();
        final Set<String> instanceNamesSeen = new HashSet<>();

        log.info("Dealing with YAML config instances...");
        final Iterator<Entry<String, ConfigYaml>> it = configs.entrySet().iterator();
        final Iterator<Entry<String, ConfigYaml>> itPipeConfigs = adPipeConfigs
                        .entrySet().iterator();
        while (it.hasNext() || itPipeConfigs.hasNext()) {
            Map.Entry<String, ConfigYaml> entry;
            boolean fromPipeIterator = false;
            if (it.hasNext()) {
                entry = it.next();
            } else {
                entry = itPipeConfigs.next();
                fromPipeIterator = true;
            }

            final String name = entry.getKey();
            final ConfigYaml yamlConfig = entry.getValue();
            // AD config cache doesn't remove configs - it just overwrites.
            if (!fromPipeIterator) {
                it.remove();
            }

            final List<Map<String, Object>> configInstances =
                    ((List<Map<String, Object>>) yamlConfig.getInstances());
            if (configInstances == null || configInstances.size() == 0) {
                final String warning = "No instance found in :" + name;
                log.warn(warning);
                this.appConfig.getStatus().addInitFailedCheck(name, warning, Status.STATUS_ERROR);
                continue;
            }

            for (final Map<String, Object> configInstance : configInstances) {
                if (appConfig.isTargetDirectInstances() != isDirectInstance(configInstance)) {
                    log.info("Skipping instance '{}'. targetDirectInstances={} != jvm_direct={}",
                            name,
                            this.appConfig.isTargetDirectInstances(),
                            isDirectInstance(configInstance));
                    continue;
                }
                final String instanceName = (String) configInstance.get("name");
                if (instanceName != null) {
                    if (instanceNamesSeen.contains(instanceName)) {
                        log.warn("Found multiple instances with name: '{}'. "
                            + "Instance names should be unique, "
                            + "update the 'name' field on your instances to be unique.",
                            instanceName);
                    }
                    instanceNamesSeen.add(instanceName);
                }
                // Create a new Instance object
                log.info("Instantiating instance for: {}", name);
                final Instance instance =
                        this.app.instantiate(
                                configInstance,
                                (Map<String, Object>) yamlConfig.getInitConfig(),
                                name,
                                this.appConfig);
                newInstances.add(instance);
            }
        }

        // Process JSON configurations
        log.info("Dealing with Auto-Config instances collected...");
        if (adJsonConfigs != null) {
            for (String check : adJsonConfigs.keySet()) {
                final Map<String, Object> checkConfig =
                        (Map<String, Object>) adJsonConfigs.get(check);
                final Map<String, Object> initConfig =
                        (Map<String, Object>) checkConfig.get("init_config");
                final List<Map<String, Object>> configInstances =
                        (List<Map<String, Object>>) checkConfig.get("instances");
                final String checkName = (String) checkConfig.get("check_name");
                for (Map<String, Object> configInstance : configInstances) {
                    log.info("Instantiating instance for: " + checkName);
                    final String instanceName = (String) configInstance.get("name");
                    if (instanceName != null) {
                        if (instanceNamesSeen.contains(instanceName)) {
                            log.warn("Found multiple instances with name: '{}'. "
                                + "Instance names should be unique, "
                                + "update the 'name' field on your instances to be unique.",
                                instanceName);
                        }
                        instanceNamesSeen.add(instanceName);
                    }
                    final Instance instance =
                            this.app.instantiate(
                                    configInstance, initConfig, checkName, this.appConfig);
                    newInstances.add(instance);
                }
            }
        }

        // Enables jmxfetch telemetry if there are other checks active and it's been enabled
        if (appConfig.getJmxfetchTelemetry() && newInstances.size() >= 1) {
            log.info("Adding jmxfetch telemetry check");
            final Instance instance = this.app.instantiate(
                    this.app.getTelemetryInstanceConfig(),
                    this.app.getTelemetryInitConfig(),
                    "jmxfetch_telemetry_check",
                    this.appConfig);
            newInstances.add(instance);
        }

        final List<InstanceTask<Void>> instanceInitTasks =
                new ArrayList<>(newInstances.size());
        for (Instance instance : newInstances) {
            // create the initializing tasks
            instanceInitTasks.add(new InstanceInitializingTask(instance, forceNewConnection));
        }

        // Initialize the instances
        log.info("Started instance initialization...");

        try {
            if (!this.recoveryProcessor.ready()) {
                log.warn(
                        "Executor has to be replaced for recovery processor, "
                        + "previous one hogging threads");
                this.recoveryProcessor.stop();
                this.recoveryProcessor.setThreadPoolExecutor(
                        this.app.buildExecutorService(
                                this.appConfig.getReconnectionThreadPoolSize(),
                                RECOVERY_POOL_NAME));
            }

            final List<TaskStatusHandler> statuses =
                    this.recoveryProcessor.processTasks(
                            instanceInitTasks,
                            this.appConfig.getCollectionTimeout(),
                            TimeUnit.SECONDS,
                            new TaskMethod<Void>() {
                                @Override
                                public TaskStatusHandler invoke(
                                        final Instance instance,
                                        final Future<Void> future,
                                        final Reporter reporter) {
                                    return App.processRecoveryResults(instance, future, reporter);
                                }
                            });

            log.info("Completed instance initialization...");

            this.app.processInstantiationStatus(instanceInitTasks, statuses);

            // update with statuses
            this.app.processStatus(instanceInitTasks, statuses);
        } catch (Exception e) {
            // NADA
            log.warn("Critical issue initializing instances: " + e);
        }
    }
}
