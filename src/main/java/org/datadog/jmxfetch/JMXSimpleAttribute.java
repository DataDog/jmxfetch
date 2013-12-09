package org.datadog.jmxfetch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ReflectionException;

public class JMXSimpleAttribute extends JMXAttribute {

	private String _alias = null;
	private String _metricType;

	public JMXSimpleAttribute(MBeanAttributeInfo a, MBeanServerConnection mbs,
			ObjectInstance instance, String instance_name) {
		
		super(a, mbs, instance, instance_name);
	}

	@Override
	public LinkedList<HashMap<String, Object>> getMetrics() throws AttributeNotFoundException, 
			InstanceNotFoundException, MBeanException, ReflectionException, IOException {
		HashMap<String, Object> metric = new HashMap<String, Object>();	
		
		metric.put("alias", _getAlias());
		metric.put("value", _getValue());
		metric.put("tags", this.tags);
		metric.put("metric_type", _getMetricType());
		LinkedList<HashMap<String, Object>> metrics = new LinkedList<HashMap<String, Object>>();
		metrics.add(metric);
		return metrics;
	}
	
	
	public boolean match(Configuration configuration) {
		return matchDomain(configuration) 
				&& matchBean(configuration) 
				&& matchAttribute(configuration) 
				&& !(
					excludeMatchDomain(configuration) 
					|| excludeMatchBean(configuration)
					|| _excludeMatchAttribute(configuration));
	}
	
	private boolean _excludeMatchAttribute(Configuration configuration) {
		if (configuration.exclude.get("attribute") == null) {
			return false;
			
		} else if ((configuration.exclude.get("attribute") instanceof LinkedHashMap<?, ?>) 
				&&  ((LinkedHashMap<String, Object>)(configuration.exclude.get("attribute"))).containsKey(attributeName)) {
			return true;
			
		} else if ((configuration.exclude.get("attribute") instanceof ArrayList<?> 
				&& ((ArrayList<String>)(configuration.exclude.get("attribute"))).contains(attributeName))) {
			return true;
		}
		return false;
	}
	
	private boolean matchAttribute(Configuration configuration) {
		if (configuration.include.get("attribute") == null) {
			return true;
			
		} else if ((configuration.include.get("attribute") instanceof LinkedHashMap<?, ?>) 
				&&  ((LinkedHashMap<String, Object>)(configuration.include.get("attribute"))).containsKey(attributeName)) {
			return true;
			
		} else if ((configuration.include.get("attribute") instanceof ArrayList<?> 
				&& ((ArrayList<String>)(configuration.include.get("attribute"))).contains(attributeName))) {
			return true;
		}
		
		return false;
	}
	
	private String _getAlias() {
		if (this._alias != null) {
			return this._alias;
		
		} else if (this.matching_conf.include.get("attribute") instanceof LinkedHashMap<?, ?>) {
			this._alias =  ((LinkedHashMap<String, LinkedHashMap<String, String>>)(this.matching_conf.include.get("attribute"))).get(this.attribute.getName()).get("alias");	
		
		} else if (this.matching_conf.conf.get("metric_prefix") != null) {
			this._alias = this.matching_conf.conf.get("metric_prefix") + "."+beanName.split(":")[0] + "." + this.attributeName;
		
		} else {
			this._alias = "jmx." + beanName.split(":")[0] + "." + this.attributeName;
		}
		
		this._alias = convertMetricName(this._alias);
		return this._alias; 
	}
	
	private String _getMetricType() {
		if (this._metricType != null) {
			return this._metricType;
		
		} else if (this.matching_conf.include.get("attribute") instanceof LinkedHashMap<?, ?>) {
			this._metricType = ((LinkedHashMap<String, LinkedHashMap<String, String>>)(this.matching_conf.include.get("attribute"))).get(this.attributeName).get("metric_type");	
			if ( this._metricType == null){
				this._metricType =((LinkedHashMap<String, LinkedHashMap<String, String>>)(this.matching_conf.include.get("attribute"))).get(this.attributeName).get("type");
			}
		} 
		
		if (this._metricType == null) {
			// Default to gauge
			this._metricType = "gauge";
		}
		
		return this._metricType;
	}
	
	private double _getValue() throws AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException, IOException, NumberFormatException {
		Object value = this.getJmxValue();
		return _getValueAsDouble(value);	
	}

}
