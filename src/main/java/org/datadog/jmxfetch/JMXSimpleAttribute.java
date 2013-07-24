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

	private String alias = null;
	private String metric_type;

	public JMXSimpleAttribute(MBeanAttributeInfo a, MBeanServerConnection mbs,
			ObjectInstance instance, String instance_name) {
		super(a, mbs, instance, instance_name);
		
	}

	@Override
	public LinkedList<HashMap<String, Object>> get_metrics() throws AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException, IOException {
		HashMap<String, Object> metric = new HashMap<String, Object>();	
		metric.put("alias", get_alias());
		metric.put("value", get_value());
		metric.put("tags", this.tags);
		metric.put("metric_type", get_metric_type());
		LinkedList<HashMap<String, Object>> metrics = new LinkedList<HashMap<String, Object>>();
		metrics.add(metric);
		return metrics;
	}
	
	
	public boolean match(Configuration conf)
	{
		return match_domain(conf) && match_bean(conf) && match_attribute(conf) && !(exclude_match_domain(conf) || exclude_match_bean(conf) || exclude_match_attribute(conf));
	}
	
	private boolean exclude_match_attribute(Configuration conf)
	{
		if (conf.exclude.get("attribute") == null)
		{
			return false;
		}
		if ((conf.exclude.get("attribute") instanceof LinkedHashMap<?, ?>) &&  ((LinkedHashMap<String, Object>)(conf.exclude.get("attribute"))).containsKey(attribute_name))
		{
			return true;
		}
		if ((conf.exclude.get("attribute") instanceof ArrayList<?> && ((ArrayList<String>)(conf.exclude.get("attribute"))).contains(attribute_name)))
		{
			return true;
		}
		return false;
	}
	
	private boolean match_attribute(Configuration conf)
	{
		if (conf.include.get("attribute") == null)
		{
			return true;
		}
		if ((conf.include.get("attribute") instanceof LinkedHashMap<?, ?>) &&  ((LinkedHashMap<String, Object>)(conf.include.get("attribute"))).containsKey(attribute_name))
		{
			return true;
		}
		if ((conf.include.get("attribute") instanceof ArrayList<?> && ((ArrayList<String>)(conf.include.get("attribute"))).contains(attribute_name)))
		{
			return true;
		}
		return false;
	}
	
	private String get_alias()
	{
		if (this.alias != null)
		{
			return this.alias;
		}
		
		if (this.matching_conf.include.get("attribute") instanceof LinkedHashMap<?, ?>)
		{
			this.alias =  ((LinkedHashMap<String, LinkedHashMap<String, String>>)(this.matching_conf.include.get("attribute"))).get(this.attribute.getName()).get("alias");	
		}
		else if (this.matching_conf.conf.get("metric_prefix") != null)
		{
			this.alias = this.matching_conf.conf.get("metric_prefix")+"."+bean_name.split(":")[0]+"."+this.attribute_name;
		}
		else {
			this.alias = "jmx."+bean_name.split(":")[0]+"."+this.attribute_name;
		}
		this.alias = convert_metric_name(this.alias);
		return this.alias; 
	}
	
	private String get_metric_type()
	{
		if (this.metric_type != null)
		{
			return this.metric_type;
		}
		if (this.matching_conf.include.get("attribute") instanceof LinkedHashMap<?, ?>)
		{	
			this.metric_type =((LinkedHashMap<String, LinkedHashMap<String, String>>)(this.matching_conf.include.get("attribute"))).get(this.attribute_name).get("metric_type");	
		}
		if (this.metric_type == null) {
			this.metric_type = "gauge";
		}
		return this.metric_type;
	}
	
	private double get_value() throws AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException, IOException
	{
		Object value = this.get_jmx_value();
		
		if (value instanceof String)
		{
			return Double.parseDouble((String)value);
		}
		else if (value instanceof Integer)
		{
			return new Double((Integer)(value));
		}
		else if (value instanceof Double)
		{
			return (Double)value;
		}
		
		
		Long l = new Long((Long) value);
		return l.doubleValue();
		
		
	}

}
