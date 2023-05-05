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
    private final Map<String, List<Metric>> registeredBeans;
    private final MetricDAO mDao;

    public BeanManager(MBeanServer mBeanServer, MetricDAO mDao) {
        this.mBeanServer = mBeanServer;
        this.registeredBeans = new HashMap<>();
        this.mDao = mDao;
    }

    private ObjectName getObjName(String domain, Metric metric) throws MalformedObjectNameException {
        return new ObjectName(domain + ":name=" + metric.name);
    }

    public void setMBeanState(String beanDomain, int numDesiredBeans) {
        RandomIdentifier idGen = new RandomIdentifier();
        ArrayList<Metric> newlyRegisteredBeans = new ArrayList<>();
        int numExistingBeans = 0;
        List<Metric> existingBeans = this.registeredBeans.get(beanDomain);
        if (registeredBeans.containsKey(beanDomain)) {
            numExistingBeans = existingBeans.size();
        }
        if (numExistingBeans == numDesiredBeans) {
            // Already have all the beans we want, nothing to do
            return;
        } else if (numExistingBeans > numDesiredBeans) {
            // Too many beans, unregister some
            int beansToRemove = numExistingBeans - numDesiredBeans;

            // Pop beans off until we get to desired amount
            for (int i = 0; i < beansToRemove; i++) {
                Metric m = existingBeans.get(0);
                try {
                    this.mBeanServer.unregisterMBean(getObjName(beanDomain, m));
                    existingBeans.remove(0);
                } catch (MBeanRegistrationException | InstanceNotFoundException | MalformedObjectNameException e) {
                    log.warn("Could not unregister bean");
                    e.printStackTrace();
                }
            }
            registeredBeans.put(beanDomain, existingBeans);
        } else if (numExistingBeans < numDesiredBeans) {
            int newBeansToBeAdded = numDesiredBeans - numExistingBeans;
            for (int i = 0; i < newBeansToBeAdded; i++) {
                Metric metric = new Metric("Bean-" + idGen.generateIdentifier(), mDao);
                try {
                    ObjectName obj = getObjName(beanDomain, metric);
                    log.debug("Registering bean with ObjectName: {}", obj);
                    this.mBeanServer.registerMBean(metric, obj);
                    newlyRegisteredBeans.add(metric);
                } catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException | MalformedObjectNameException e) {
                    log.error("Could not add bean {} for domain {}", metric.name, beanDomain);
                    e.printStackTrace();
                }
            }
            ArrayList<Metric> totalBeans = new ArrayList<>(newlyRegisteredBeans);
            if (existingBeans != null) {
                totalBeans.addAll(existingBeans);
            }
            registeredBeans.put(beanDomain, totalBeans);
        }

    }

    public Optional<List<Metric>> getMBeanState(String domain) {
        return Optional.ofNullable(registeredBeans.get(domain));
    }
}
