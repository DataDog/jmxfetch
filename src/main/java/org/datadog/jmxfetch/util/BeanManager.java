package org.datadog.jmxfetch.util;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import org.datadog.jmxfetch.Instance;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BeanManager {

    private MBeanServer mbs;
    private Map<String, BeanJavaApp> registeredBeans;
   

    public BeanManager() {
        mbs = (mbs == null) ? ManagementFactory.getPlatformMBeanServer() : mbs;
        registeredBeans = new HashMap<>();
    }

    private ObjectName getObjName(String domain,String name) throws MalformedObjectNameException {
        return new ObjectName(domain + ":name=" + name);
    }

    public BeanJavaApp createJMXBean(Instance instance){
        //if the instance already has a bean just return that 
        if (registeredBeans.get(instance.getCheckName()) != null){
            return registeredBeans.get(instance.getCheckName());
        }

        BeanJavaApp bean = new BeanJavaApp();
        bean.setInstance(instance.getName());
        registeredBeans.put(instance.getCheckName(), bean);

        try {
                ObjectName name = getObjName("JMXFetch" , instance.getCheckName());
                mbs.registerMBean(bean,name);

        } catch (MalformedObjectNameException | InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException e) {
                    log.warn("Could not register bean");
                    e.printStackTrace();
        }
        return bean;
    }

}
