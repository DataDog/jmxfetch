package org.datadog.jmxfetch;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

class ConfigYaml {
    private static final ThreadLocal<Yaml> YAML =
        new ThreadLocal<Yaml>() {
            @Override
            public Yaml initialValue() {
                return new Yaml();
            }
        };

    private final Map<Object, Object> parsedYaml;

    public ConfigYaml(InputStream yamlInputStream) {
        parsedYaml = parse(yamlInputStream);
    }

    public Object getInstances() {
        return parsedYaml.get("instances");
    }

    public Object getInitConfig() {
        return parsedYaml.get("init_config");
    }

    public static <T> T parse(InputStream yamlInputStream) {
        return YAML.get().load(yamlInputStream);
    }

    public static String dump(Object parsedYaml) {
        return YAML.get().dump(parsedYaml);
    }
}
