package org.datadog.jmxfetch;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;

import org.yaml.snakeyaml.Yaml;

public class YamlParser {
	
	private HashMap<Object, Object> parsed_yaml;

	public YamlParser(String path) throws FileNotFoundException {
		
		InputStream yaml_file = new FileInputStream(path);
		Yaml yaml = new Yaml();
	    parsed_yaml = (HashMap<Object, Object>) yaml.load(yaml_file);
	   
	}
	
	public Object get_init_config() {
		return parsed_yaml.get("init_config");
	}
	
	public Object get_instances() {
		return parsed_yaml.get("instances");
	}
	
	public Object get_parsed_yaml() {
		return parsed_yaml;
	}
	
}
