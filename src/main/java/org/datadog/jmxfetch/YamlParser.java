package org.datadog.jmxfetch;

import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

import java.io.InputStream;
import java.util.Map;

class YamlParser {

    private final Map<Object, Object> parsedYaml;

    public YamlParser(InputStream yamlInputStream) {
        LoadSettings settings = LoadSettings.builder().build();
        parsedYaml = (Map<Object, Object>) new Load(settings).loadFromInputStream(yamlInputStream);
    }

    public Object getYamlInstances() {
        return parsedYaml.get("instances");
    }

    public Object getInitConfig() {
        return parsedYaml.get("init_config");
    }
}
