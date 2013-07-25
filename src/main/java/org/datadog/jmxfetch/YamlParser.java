package org.datadog.jmxfetch;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;

import org.yaml.snakeyaml.Yaml;

public class YamlParser {
	
	private HashMap<Object, Object> parsedYaml;
	
	public YamlParser(String path) throws FileNotFoundException {
		InputStream yaml_file = new FileInputStream(path);
		Yaml yaml = new Yaml();
	    parsedYaml = (HashMap<Object, Object>) yaml.load(yaml_file);	   
	}
	
	public Object getInitConfig() {
		return parsedYaml.get("init_config");
	}
	
	public Object getYamlInstances() {
		return parsedYaml.get("instances");
	}
	
	public Object getParsedYaml() {
		return parsedYaml;
	}
	
}
