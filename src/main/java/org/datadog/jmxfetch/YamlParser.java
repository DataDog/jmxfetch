package org.datadog.jmxfetch;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashMap;

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

    public Object getParsedYaml() {
        return parsedYaml;
    }

}
