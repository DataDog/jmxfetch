package org.datadog.jmxfetch;

import java.io.InputStream;
import java.util.HashMap;

import org.yaml.snakeyaml.Yaml;

@SuppressWarnings("unchecked")
class YamlParser {

    private HashMap<Object, Object> parsedYaml;

    public YamlParser(InputStream yamlInputStream) {
        parsedYaml = (HashMap<Object, Object>) new Yaml().load(yamlInputStream);
    }

    public YamlParser(YamlParser other) {
        parsedYaml = new HashMap<Object, Object>((HashMap<Object, Object>) other.getParsedYaml());
    }

    public Object getYamlInstances() {
        return parsedYaml.get("instances");
    }

    public Object getInitConfig() {
        return parsedYaml.get("init_config");
    }

    public Object getParsedYaml() {
        return parsedYaml;
    }

}
