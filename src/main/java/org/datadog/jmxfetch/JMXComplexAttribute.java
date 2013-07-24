package org.datadog.jmxfetch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;

public class JMXComplexAttribute extends JMXAttribute {

	private HashMap<String,HashMap<String,Object>> sub_attr_list;

	public JMXComplexAttribute(MBeanAttributeInfo a, MBeanServerConnection mbs,
			ObjectInstance instance, String instance_name) {
		super(a, mbs, instance, instance_name);
		this.sub_attr_list = new HashMap<String, HashMap<String, Object>>();
	}

	private void populate_sub_attr_list(Object attr_val) {
		if (this.attribute.getType().equals("javax.management.openmbean.CompositeData"))
		{
			CompositeData data = (CompositeData) attr_val;
			for ( String key : data.getCompositeType().keySet() )
			{
				HashMap<String, Object> sub_attr_params = new HashMap<String, Object>();
				this.sub_attr_list.put(key, sub_attr_params);
			}
		}
	
	}


	@Override
	public LinkedList<HashMap<String, Object>> get_metrics()
			throws AttributeNotFoundException, InstanceNotFoundException,
			MBeanException, ReflectionException, IOException {

		LinkedList<HashMap<String, Object>> metrics = new LinkedList<HashMap<String, Object>>();
		Iterator<Entry<String, HashMap<String, Object>>> it = this.sub_attr_list.entrySet().iterator();

		while(it.hasNext())
		{
			Map.Entry<String, HashMap<String, Object>> pairs = (Map.Entry<String, HashMap<String, Object>>)it.next();
			String sub_attr = pairs.getKey();
			HashMap<String, Object> metric = pairs.getValue();
			
			if (metric.get("alias") == null)
			{
				metric.put("alias", convert_metric_name(get_alias(sub_attr)));
			}
			if (metric.get("metric_type") == null)
			{
				metric.put("metric_type", get_metric_type(sub_attr));
			}
			if (metric.get("tags") == null)
			{
				metric.put("tags", this.tags);
			}

			metric.put("value", get_value(sub_attr));
			metrics.add(metric);
			
		}
		return metrics;

	}

	private double get_value(String sub_attr) throws AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException, IOException {
		Object value = this.get_jmx_value();

		if (this.attribute.getType().equals("javax.management.openmbean.CompositeData"))
		{
			CompositeData data = (CompositeData) value;
			Object sub_value = data.get(sub_attr);
			if (sub_value instanceof String)
			{
				return Double.parseDouble((String)value);
			}
			else if (sub_value instanceof Integer)
			{
				return new Double((Integer)(sub_value));
			}
			else if (sub_value instanceof Double)
			{
				return (Double)sub_value;
			}
			
			Long l = new Long((Long) sub_value);
			return l.doubleValue();

		}
		return 0;
	}

	private Object get_metric_type(String sub_attr) {
		String sub_attr_name = this.attribute.getName() + "." + sub_attr;
		if (this.matching_conf.include.get("attribute") instanceof LinkedHashMap<?, ?>)
		{	
			return ((LinkedHashMap<String, LinkedHashMap<String, String>>)(this.matching_conf.include.get("attribute"))).get(sub_attr_name).get("metric_type");	
		}
		return "gauge";
	}

	private String get_alias(String sub_attr)
	{
		String sub_attr_name = this.attribute.getName() + "." + sub_attr;

		if (this.matching_conf.include.get("attribute") instanceof LinkedHashMap<?, ?>)
		{
			return ((LinkedHashMap<String, LinkedHashMap<String, String>>)(this.matching_conf.include.get("attribute"))).get(sub_attr_name).get("alias");	
		}
		if (this.matching_conf.conf.get("metric_prefix") != null)
		{
			return this.matching_conf.conf.get("metric_prefix")+"."+bean_name.split(":")[0]+ "." + sub_attr_name;
		}
		return "jmx."+bean_name.split(":")[0] + "." + sub_attr_name;
	}



	@Override
	public boolean match(Configuration conf) {
		if (!match_domain(conf) || !match_bean(conf) || exclude_match_domain(conf) || exclude_match_bean(conf)) 
		{
			return false;
		}
		try {
			populate_sub_attr_list(get_jmx_value());
		} catch (Exception e) {
			return false;
		}

		return match_attribute(conf) && !exclude_match_attribute(conf);
	}

	private boolean match_sub_attribute(LinkedHashMap<String, Object> params, String attr_name)
	{
		if ((params.get("attribute") instanceof LinkedHashMap<?, ?>) &&  ((LinkedHashMap<String, Object>)(params.get("attribute"))).containsKey(attr_name))
		{
			return true;
		}
		if ((params.get("attribute") instanceof ArrayList<?> && ((ArrayList<String>)(params.get("attribute"))).contains(attr_name)))
		{
			return true;
		}
		return false;

	}

	private boolean match_attribute(Configuration conf)
	{
		if (match_sub_attribute(conf.include, this.attribute_name))
		{
			return true;
		}		
		
		Iterator<String> it = this.sub_attr_list.keySet().iterator();
		
		while(it.hasNext())
		{
			String sub_attr = it.next();
			if (!match_sub_attribute(conf.include, this.attribute_name + "." + sub_attr))
			{
				it.remove();
			}
		}
		
		if (this.sub_attr_list.size() > 0)
		{
			return true;
		}

		return false;

	}
	
	
	private boolean exclude_match_attribute(Configuration conf)
	{
		if (match_sub_attribute(conf.exclude, this.attribute_name))
		{
			return true;
		}		
		
		Iterator<String> it = this.sub_attr_list.keySet().iterator();
	
		while(it.hasNext())
		{
			String sub_attr = it.next();
			if (match_sub_attribute(conf.exclude, this.attribute_name + "." + sub_attr))
			{
				it.remove();
			}
		}
		
		if (this.sub_attr_list.size() > 0)
		{
			return false;
		}

		return true;

	}

}
