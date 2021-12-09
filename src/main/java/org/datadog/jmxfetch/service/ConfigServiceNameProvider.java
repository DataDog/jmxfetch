package org.datadog.jmxfetch.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ConfigServiceNameProvider provides the list of service names based on the config maps passed to
 * its constructor.
 */
public class ConfigServiceNameProvider implements ServiceNameProvider {
    private List<String> serviceNames;
    private ServiceNameProvider additionalServiceNames;


    /**
     * Builds a ConfigServiceNameProvider based on the maps of an instance config and init config,
     * returning the initialized ServiceNameProvider.
     *
     * @param instanceMap The instance config map
     * @param initConfig The init config map
     */
    public ConfigServiceNameProvider(
            Map<String, Object> instanceMap,
            Map<String, Object> initConfig,
            ServiceNameProvider additionalServiceNames) {
        List<String> services = compileServiceList(instanceMap);
        if (services.size() == 0) {
            services = compileServiceList(initConfig);
        }
        this.serviceNames = services;
        this.additionalServiceNames = additionalServiceNames;
    }

    /**
     * Returns a String Iterable with the relevant services defined in the init config and instance
     * maps supplied in the constructor, as well as any additional services derived from the
     * additional ServiceNameProvider (if any).
     *
     * @return the service String Iterable.
     */
    public Iterable<String> getServiceNames() {
        if (this.additionalServiceNames == null) {
            return this.serviceNames;
        }

        List<String> names = new ArrayList<String>(this.serviceNames);
        for (String service : this.additionalServiceNames.getServiceNames()) {
            names.add(service);
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private static List<String> compileServiceList(Map<String, Object> config) {
        List<String> services = new ArrayList<>();
        if (config == null || !config.containsKey("service")) {
            return services;
        }

        try {
            String svc = (String) config.get("service");
            if (svc != null && !svc.isEmpty()) {
                services.add(svc);
            }
        } catch (ClassCastException e) {
            // must be a list then...
            services = (List<String>) config.get("service");
        }

        return services;
    }
}
