package org.datadog.misbehavingjmxserver;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BeanManager {
    private final MBeanServer mBeanServer;
    private final Map<String, List<DynamicMBeanMetrics>> registeredBeans;
    private final MetricDAO mDao;
    private final RandomIdentifier idGen;
    static final long ATTRIBUTE_REFRESH_INTERVAL = 10;
    private final ScheduledExecutorService executor;

    public BeanManager(MBeanServer mBeanServer, MetricDAO mDao, long rngSeed) {
        this.mBeanServer = mBeanServer;
        this.registeredBeans = new HashMap<>();
        this.mDao = mDao;
        this.idGen = new RandomIdentifier(rngSeed);
        this.executor = Executors.newSingleThreadScheduledExecutor();
        runAttributeUpdateLoop();
    }

    private ObjectName getObjName(String domain, DynamicMBeanMetrics metric) throws MalformedObjectNameException {
        return new ObjectName(domain + ":name=" + metric.name);
    }


    public void clearDomainBeans(String beanDomain){
        List<DynamicMBeanMetrics> beansList = this.registeredBeans.getOrDefault(beanDomain,new ArrayList<DynamicMBeanMetrics>());
        int size = beansList.size();

        for (int i = 0; i < size; i++){
            DynamicMBeanMetrics metric = beansList.get(0);
            try {
                ObjectName obj = getObjName(beanDomain, metric);
                this.mBeanServer.unregisterMBean(obj);
                beansList.remove(0);
            } catch (MBeanRegistrationException | InstanceNotFoundException | MalformedObjectNameException e) {
                log.warn("Could not unregister bean {} for domain {}", metric.name, beanDomain, e);
            }
        }
        registeredBeans.put(beanDomain, new ArrayList<DynamicMBeanMetrics>());
    }

    public void setMBeanState(String beanDomain, BeanSpec domainSpec) {
        clearDomainBeans(beanDomain);
        ArrayList<DynamicMBeanMetrics> beansList = new ArrayList<DynamicMBeanMetrics>();

        for (int i = 0; i < domainSpec.beanCount; i++) {
            DynamicMBeanMetrics metric = new DynamicMBeanMetrics("Bean-" + idGen.generateIdentifier(), domainSpec.scalarAttributeCount,
                    domainSpec.tabularAttributeCount, domainSpec.compositeValuesPerTabularAttribute, mDao);
            try {
                ObjectName obj = getObjName(beanDomain, metric);
                log.debug("Registering bean with ObjectName: {}", obj);
                this.mBeanServer.registerMBean(metric, obj);
                beansList.add(metric);
            } catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException | MalformedObjectNameException e) {
                log.warn("Could not add bean {} for domain {}", metric.name, beanDomain, e);
            }
        }
        registeredBeans.put(beanDomain, beansList);


    }

    public Optional<List<DynamicMBeanMetrics>> getMBeanState(String domain) {
        return Optional.ofNullable(registeredBeans.get(domain));
    }

    public void Do() {
        for (Map.Entry<String,List<DynamicMBeanMetrics>> beanDomainEntry : registeredBeans.entrySet()) {
            List<DynamicMBeanMetrics> beansList = beanDomainEntry.getValue();
            for (DynamicMBeanMetrics bean: beansList){
                bean.updateBeanAttributes();
            }
        }
    }

    void runAttributeUpdateLoop() {
        Runnable task = () -> {
            this.Do();
        };
        executor.scheduleAtFixedRate(task, 0, ATTRIBUTE_REFRESH_INTERVAL, TimeUnit.SECONDS);
    }


}
