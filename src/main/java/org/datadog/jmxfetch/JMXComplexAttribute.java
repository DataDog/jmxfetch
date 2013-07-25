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

	private HashMap<String,HashMap<String,Object>> subAttributeList;

	public JMXComplexAttribute(MBeanAttributeInfo a, MBeanServerConnection mbs,
			ObjectInstance instance, String instance_name) {
		
		super(a, mbs, instance, instance_name);
		this.subAttributeList = new HashMap<String, HashMap<String, Object>>();
	}

	private void _populateSubAttributeList(Object attributeValue) {
		if (this.attribute.getType().equals("javax.management.openmbean.CompositeData")) {
			CompositeData data = (CompositeData) attributeValue;
			for ( String key : data.getCompositeType().keySet() ) {
				HashMap<String, Object> sub_attr_params = new HashMap<String, Object>();
				this.subAttributeList.put(key, sub_attr_params);
			}
		}
	
	}


	@Override
	public LinkedList<HashMap<String, Object>> getMetrics()
			throws AttributeNotFoundException, InstanceNotFoundException,
			MBeanException, ReflectionException, IOException {

		LinkedList<HashMap<String, Object>> metrics = new LinkedList<HashMap<String, Object>>();
		Iterator<Entry<String, HashMap<String, Object>>> it = this.subAttributeList.entrySet().iterator();

		while(it.hasNext()) {
			Map.Entry<String, HashMap<String, Object>> pairs = (Map.Entry<String, HashMap<String, Object>>)it.next();
			String subAttribute = pairs.getKey();
			HashMap<String, Object> metric = pairs.getValue();
			
			if (metric.get("alias") == null) {
				metric.put("alias", convertMetricName(_getAlias(subAttribute)));
			}
			
			if (metric.get("metric_type") == null) {
				metric.put("metric_type", _getMetricType(subAttribute));
			}
			
			if (metric.get("tags") == null) {
				metric.put("tags", this.tags);
			}

			metric.put("value", _getValue(subAttribute));
			metrics.add(metric);
			
		}
		return metrics;

	}

	private double _getValue(String subAttribute) throws AttributeNotFoundException, InstanceNotFoundException, 
				MBeanException, ReflectionException, IOException {
		
		Object value = this.getJmxValue();

		if (this.attribute.getType().equals("javax.management.openmbean.CompositeData")) {
			CompositeData data = (CompositeData) value;
			Object sub_value = data.get(subAttribute);
			
			if (sub_value instanceof String) {
				return Double.parseDouble((String)value);
				
			} else if (sub_value instanceof Integer) {
				return new Double((Integer)(sub_value));
				
			} else if (sub_value instanceof Double) {
				return (Double)sub_value;
			}
			
			Long l = new Long((Long) sub_value);
			return l.doubleValue();

		}
		return 0;
	}

	private Object _getMetricType(String subAttribute) {
		String subAttributeName = this.attribute.getName() + "." + subAttribute;
		
		if (this.matching_conf.include.get("attribute") instanceof LinkedHashMap<?, ?>) {	
			return ((LinkedHashMap<String, LinkedHashMap<String, String>>)(this.matching_conf.include.get("attribute"))).get(subAttributeName).get("metric_type");	
		}
		
		return "gauge";
	}

	private String _getAlias(String subAttribute)
	{
		String subAttributeName = this.attribute.getName() + "." + subAttribute;

		if (this.matching_conf.include.get("attribute") instanceof LinkedHashMap<?, ?>) {
			return ((LinkedHashMap<String, LinkedHashMap<String, String>>)(this.matching_conf.include.get("attribute"))).get(subAttributeName).get("alias");	
		
		} else if (this.matching_conf.conf.get("metric_prefix") != null) {
			return this.matching_conf.conf.get("metric_prefix")+"."+beanName.split(":")[0]+ "." + subAttributeName;
		
		}
		
		return "jmx."+beanName.split(":")[0] + "." + subAttributeName;
	}



	@Override
	public boolean match(Configuration configuration) {
		if (!matchDomain(configuration) 
				|| !matchBean(configuration) 
				|| excludeMatchDomain(configuration) 
				|| excludeMatchBean(configuration)) {
			return false;
		}
		
		try {
			_populateSubAttributeList(getJmxValue());
		} catch (Exception e) {
			return false;
		}

		return _matchAttribute(configuration) && !_excludeMatchAttribute(configuration);
	}

	private boolean _matchSubAttribute(LinkedHashMap<String, Object> params, String subAttributeName)
	{
		if ((params.get("attribute") instanceof LinkedHashMap<?, ?>) &&  ((LinkedHashMap<String, Object>)(params.get("attribute"))).containsKey(subAttributeName)) {
			return true;
		
		} else if ((params.get("attribute") instanceof ArrayList<?> && ((ArrayList<String>)(params.get("attribute"))).contains(subAttributeName))) {
			return true;
		}
		return false;

	}

	private boolean _matchAttribute(Configuration configuration) {
		if (_matchSubAttribute(configuration.include, this.attributeName)) {
			return true;
		}		
		
		Iterator<String> it = this.subAttributeList.keySet().iterator();
		
		while(it.hasNext()) {
			String subAttribute = it.next();
			if (!_matchSubAttribute(configuration.include, this.attributeName + "." + subAttribute)) {
				it.remove();
			}
		}
		
		if (this.subAttributeList.size() > 0) {
			return true;
		}

		return false;

	}
	
	
	private boolean _excludeMatchAttribute(Configuration configuration) {
		
		if (_matchSubAttribute(configuration.exclude, this.attributeName)) {
			return true;
		}		
		
		Iterator<String> it = this.subAttributeList.keySet().iterator();
	
		while(it.hasNext()) {
			String subAttribute = it.next();
			if (_matchSubAttribute(configuration.exclude, this.attributeName + "." + subAttribute)) {
				it.remove();
			}
		}
		
		if (this.subAttributeList.size() > 0) {
			return false;
		}

		return true;

	}

}
