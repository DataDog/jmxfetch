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
    private final Map<String, List<Metrics>> registeredBeans;
    private final MetricsDAO mDao;

    public BeanManager(MBeanServer mBeanServer, MetricsDAO mDao) {
        this.mBeanServer = mBeanServer;
        this.registeredBeans = new HashMap<>();
        this.mDao = mDao;
    }

    private ObjectName getObjName(String domain, Metrics metric) throws MalformedObjectNameException {
        return new ObjectName(domain + ":name=" + metric.name);
    }

    public void setMBeanState(BeanSpec beanSpec) {
        RandomIdentifier idGen = new RandomIdentifier();
        ArrayList<Metrics> newlyRegisteredBeans = new ArrayList<>();
        int numExistingBeans = 0;
        if (registeredBeans.containsKey(beanSpec.domain)) {
            numExistingBeans = registeredBeans.get(beanSpec.domain).size();
        }
        if (numExistingBeans == beanSpec.numDesiredBeans) {
            // Already have all the beans we want, nothing to do
            return;
        } else if (numExistingBeans > beanSpec.numDesiredBeans) {
            // Too many beans, unregister some
            int beansToRemove = numExistingBeans - beanSpec.numDesiredBeans;
            List<Metrics> existingBeans = this.registeredBeans.get(beanSpec.domain);

            // Pop beans off until we get to desired amount
            for (int i = 0; i < beansToRemove; i++) {
                Metrics m = existingBeans.get(0);
                try {
                    this.mBeanServer.unregisterMBean(getObjName(beanSpec.domain, m));
                    existingBeans.remove(0);
                } catch (MBeanRegistrationException | InstanceNotFoundException | MalformedObjectNameException e) {
                    log.warn("Could not unregister bean");
                    e.printStackTrace();
                }
            }
            registeredBeans.put(beanSpec.domain, existingBeans);
        } else if (numExistingBeans < beanSpec.numDesiredBeans) {
            int newBeansToBeAdded = beanSpec.numDesiredBeans - numExistingBeans;
            for (int i = 0; i < newBeansToBeAdded; i++) {
                Metrics metric = new Metrics("Bean-" + idGen.generateIdentifier(), mDao);
                try {
                    ObjectName obj = getObjName(beanSpec.domain, metric);
                    log.debug("Registering bean with ObjectName: {}", obj);
                    this.mBeanServer.registerMBean(metric, obj);
                    newlyRegisteredBeans.add(metric);
                } catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException | MalformedObjectNameException e) {
                    log.error("Could not add bean {} for domain {}", metric.name, beanSpec.domain);
                    e.printStackTrace();
                }
            }
            registeredBeans.put(beanSpec.domain, newlyRegisteredBeans);
        }

    }

    public Optional<List<Metrics>> getMBeanState(String domain) {
        return Optional.ofNullable(registeredBeans.get(domain));
    }
}
