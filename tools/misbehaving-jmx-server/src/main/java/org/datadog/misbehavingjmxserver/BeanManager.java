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

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BeanManager {
    private final MBeanServer mBeanServer;
    private final Map<String, List<DynamicMBeanMetrics>> registeredBeans;

    public BeanManager(MBeanServer mBeanServer) {
        this.mBeanServer = mBeanServer;
        this.registeredBeans = new HashMap<>();
    }

    private ObjectName getObjName(String domain, DynamicMBeanMetrics metric) throws MalformedObjectNameException {
        return new ObjectName(domain + ":name=" + metric.name);
    }


    public void clearDomainBeans(String beanDomain){
        List<DynamicMBeanMetrics> beansList = this.registeredBeans.getOrDefault(beanDomain,new ArrayList<DynamicMBeanMetrics>());
        int size = beansList.size();
        for (int i = 0; i< size; i++){
            DynamicMBeanMetrics metric = beansList.get(0);
            try {
                ObjectName obj = getObjName(beanDomain, metric);
                System.out.println("unregestered a bean in domain: " + beanDomain + "with name " + obj);
                this.mBeanServer.unregisterMBean(obj);
                beansList.remove(0);
            } catch (MBeanRegistrationException | InstanceNotFoundException | MalformedObjectNameException e) {
                log.warn("Could not unregister bean");
                e.printStackTrace();
            }
        }
        registeredBeans.put(beanDomain, new ArrayList<DynamicMBeanMetrics>());
    }

    public void setMBeanState(String beanDomain, BeanSpec domainSpec) {
        clearDomainBeans(beanDomain);
        RandomIdentifier idGen = new RandomIdentifier();
        ArrayList<DynamicMBeanMetrics> beansList = new ArrayList<DynamicMBeanMetrics>();

        for (int i = 0; i < domainSpec.bean_count; i++) {
            DynamicMBeanMetrics metric = new DynamicMBeanMetrics("Bean-" + idGen.generateIdentifier(), domainSpec.attribute_count, domainSpec.tabular_count, domainSpec.composite_count);
            try {
                ObjectName obj = getObjName(beanDomain, metric);
                log.debug("Registering bean with ObjectName: {}", obj);
                this.mBeanServer.registerMBean(metric, obj);
                beansList.add(metric);
            } catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException | MalformedObjectNameException e) {
                log.error("Could not add bean {} for domain {}", metric.name, beanDomain);
                e.printStackTrace();
            }
        }
        registeredBeans.put(beanDomain, beansList);


    }

    public Optional<List<DynamicMBeanMetrics>> getMBeanState(String domain) {
        return Optional.ofNullable(registeredBeans.get(domain));
    }
}
