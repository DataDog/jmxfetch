package org.datadog.jmxfetch;

import org.datadog.jmxfetch.util.JavaVersion;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

class ConfigYaml {
    private static final boolean USE_YAML_ENGINE = JavaVersion.atLeastJava(8);

    // used on Java 8+
    private static final ThreadLocal<Load> YAML_LOAD;
    private static final ThreadLocal<Dump> YAML_DUMP;

    // used on Java 7
    private static final ThreadLocal<Yaml> YAML_LEGACY;

    static {
        if (USE_YAML_ENGINE) {
            YAML_LOAD =
                new ThreadLocal<Load>() {
                    @Override
                    public Load initialValue() {
                        return new Load(LoadSettings.builder().build());
                    }
                };
            YAML_DUMP =
                new ThreadLocal<Dump>() {
                    @Override
                    public Dump initialValue() {
                        return new Dump(DumpSettings.builder().build());
                    }
                };

            YAML_LEGACY = null;
        } else {
            YAML_LEGACY =
                new ThreadLocal<Yaml>() {
                    @Override
                    public Yaml initialValue() {
                        return new Yaml();
                    }
                };

            YAML_LOAD = null;
            YAML_DUMP = null;
        }
    }

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

    @SuppressWarnings("unchecked")
    public static <T> T parse(InputStream yamlInputStream) {
        if (USE_YAML_ENGINE) {
            return (T) YAML_LOAD.get().loadFromInputStream(yamlInputStream);
        } else {
            return YAML_LEGACY.get().load(yamlInputStream);
        }
    }

    public static String dump(Object parsedYaml) {
        if (USE_YAML_ENGINE) {
            return YAML_DUMP.get().dumpToString(parsedYaml);
        } else {
            return YAML_LEGACY.get().dump(parsedYaml);
        }
    }
}
