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
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BeanManager {
    private final MBeanServer mBeanServer;
    private final Map<String, List<FourAttributeMetric>> registeredBeans;
    private final MetricDAO mDao;

    public BeanManager(MBeanServer mBeanServer, MetricDAO mDao) {
        this.mBeanServer = mBeanServer;
        this.registeredBeans = new HashMap<>();
        this.mDao = mDao;
    }

    protected static ObjectName getObjName(String domain, FourAttributeMetric metric) throws MalformedObjectNameException {
        return getObjName(domain, metric.name);
    }

    protected static ObjectName getObjName(String domain, String name) throws MalformedObjectNameException {
        final Hashtable<String, String> properties = new Hashtable<>();
        properties.put("name", name);
        return new ObjectName(domain, properties);
    }

    protected static ObjectName getObjName(String domain, String name, String type) throws MalformedObjectNameException {
        final Hashtable<String, String> properties = new Hashtable<>();
        properties.put("name", name);
        properties.put("type", type);
        return new ObjectName(domain, properties);
    }

    public void setMBeanState(String beanDomain, int numDesiredBeans) {
        RandomIdentifier idGen = new RandomIdentifier();
        ArrayList<FourAttributeMetric> newlyRegisteredBeans = new ArrayList<>();
        int numExistingBeans = 0;
        List<FourAttributeMetric> existingBeans = this.registeredBeans.get(beanDomain);
        if (this.registeredBeans.containsKey(beanDomain)) {
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
                final FourAttributeMetric m = existingBeans.get(0);
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
                final FourAttributeMetric metric = new FourAttributeMetric("Bean-" + idGen.generateIdentifier(), mDao);
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
            ArrayList<FourAttributeMetric> totalBeans = new ArrayList<>(newlyRegisteredBeans);
            if (existingBeans != null) {
                totalBeans.addAll(existingBeans);
            }
            registeredBeans.put(beanDomain, totalBeans);
        }
    }

    public Optional<List<FourAttributeMetric>> getMBeanState(String domain) {
        return Optional.ofNullable(registeredBeans.get(domain));
    }
}
