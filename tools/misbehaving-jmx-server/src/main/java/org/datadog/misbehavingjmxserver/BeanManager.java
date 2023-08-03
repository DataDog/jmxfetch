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
    private final BeanSpec resetBeanSpec;
    private Map<String,BeanSpec> domainStateMap;



    public BeanManager(MBeanServer mBeanServer) {
        this.mBeanServer = mBeanServer;
        this.registeredBeans = new HashMap<>();
        this.domainStateMap = new HashMap<String,BeanSpec>();
        resetBeanSpec = new BeanSpec();
    }

    private ObjectName getObjName(String domain, DynamicMBeanMetrics metric) throws MalformedObjectNameException {
        return new ObjectName(domain + ":name=" + metric.name);
    }

    public void setMBeanState(String beanDomain, BeanSpec domainSpec) {
        // First we need to check if the configuration of the beans for this domain have changed
        // If so we need to erase all the beans first then recreate the beans to the desired state
        // in order to elimite unpredictible behavior with a mix of bean compositions
        if (!domainSpec.equals(resetBeanSpec)){
            if (domainStateMap.containsKey(beanDomain)){
                // Check if BeanSpecs are equal and if not then delete all the current beans in the domain
                // Note the BeanSpecs.equals ignored bean_count
                if(!domainStateMap.get(beanDomain).equals(domainSpec)){
                    System.out.println("Reseting the bean configuration for domain: " + beanDomain);
                    setMBeanState(beanDomain, resetBeanSpec);
                    // After this we continue with the rest of this call of setMBeanState updating the beans with the proper config
                }
            }
            // If this is the first time regsitering beans under this domain we want to add the beanspec
            // If we just reset our bean config we also want to update our config in domainStateMap
            domainStateMap.put(beanDomain,domainSpec);
        }

        int numDesiredBeans = domainSpec.bean_count;
        RandomIdentifier idGen = new RandomIdentifier();
        ArrayList<DynamicMBeanMetrics> newlyRegisteredBeans = new ArrayList<>();
        int numExistingBeans = 0;
        List<DynamicMBeanMetrics> existingBeans = this.registeredBeans.getOrDefault(beanDomain,new ArrayList<DynamicMBeanMetrics>());
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
                DynamicMBeanMetrics m = existingBeans.get(0);
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
                DynamicMBeanMetrics metric = new DynamicMBeanMetrics("Bean-" + idGen.generateIdentifier(), domainSpec.attribute_count, domainSpec.tabular_count, domainSpec.composite_count);
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
            ArrayList<DynamicMBeanMetrics> totalBeans = new ArrayList<>(newlyRegisteredBeans);
            if (existingBeans != null) {
                totalBeans.addAll(existingBeans);
            }
            registeredBeans.put(beanDomain, totalBeans);
        }

    }

    public Optional<List<DynamicMBeanMetrics>> getMBeanState(String domain) {
        return Optional.ofNullable(registeredBeans.get(domain));
    }
}
