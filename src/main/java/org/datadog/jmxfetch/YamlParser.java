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
		init(yaml_file);
	}
	
	public YamlParser(InputStream yamlInputStream) {
		init(yamlInputStream);
	}
		
	@SuppressWarnings("unchecked")
	private void init(InputStream yamlInputStream) {
		Yaml yaml = new Yaml();
	    parsedYaml = (HashMap<Object, Object>) yaml.load(yamlInputStream);	   
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
	
	@SuppressWarnings("unchecked")
	public boolean isJmx() {
		try {
			return (Boolean) ((HashMap<Object, Object>) parsedYaml.get("init_config")).get("is_jmx");
		} catch(Exception e) {
			return false;
		}
	}
	
}
