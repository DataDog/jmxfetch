package org.datadog.jmxfetch;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

@SuppressWarnings("unchecked")
class YamlParser {

  private Map<Object, Object> parsedYaml;

  public YamlParser(InputStream yamlInputStream) {
    parsedYaml = (Map<Object, Object>) new Yaml().load(yamlInputStream);
  }

  public YamlParser(YamlParser other) {
    parsedYaml = new HashMap<Object, Object>((Map<Object, Object>) other.getParsedYaml());
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
