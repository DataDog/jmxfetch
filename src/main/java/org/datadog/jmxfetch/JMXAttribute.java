package org.datadog.jmxfetch;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ReflectionException;

public abstract class JMXAttribute {

	protected MBeanAttributeInfo attribute;
	protected MBeanServerConnection mbs;
	protected ObjectInstance jmx_instance;
	protected double value;
	protected String domain;
	protected String bean_name;
	protected String attribute_name;
	protected String[] tags;
	protected Configuration matching_conf;
	protected static final List<String> SIMPLE_TYPES = Arrays.asList("long", "java.lang.String", "int", "double"); 
	protected static final List<String> COMPOSED_TYPES = Arrays.asList("javax.management.openmbean.CompositeData");

	public JMXAttribute(MBeanAttributeInfo a, MBeanServerConnection mbs, ObjectInstance jmx_instance, String instance_name)
	{
		this.attribute = a;
		this.mbs = mbs;
		this.jmx_instance = jmx_instance;
		this.bean_name = jmx_instance.getObjectName().toString();

		// A bean name is formatted like that: org.apache.cassandra.db:type=Caches,keyspace=system,cache=HintsColumnFamilyKeyCache
		// i.e. : domain:bean_parameter1,bean_parameter2
		String[] split = this.bean_name.split(":");
		this.domain = split[0];
		this.attribute_name = a.getName();
		LinkedList<String> bean_tags = new LinkedList<String>(Arrays.asList(split[1].replace("=",":").split(",")));
		bean_tags.add("instance:"+instance_name);
		this.tags = new String[bean_tags.size()];
		bean_tags.toArray(this.tags);
		this.matching_conf = null;

	}

	@Override
	public String toString()
	{
		return this.bean_name + " - " + this.attribute_name;
	}

	public abstract LinkedList<HashMap<String, Object>> get_metrics() throws AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException, IOException;

	public abstract boolean match(Configuration conf);


	protected Object get_jmx_value() throws AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException, IOException
	{
		return this.mbs.getAttribute(this.jmx_instance.getObjectName(), this.attribute.getName());
	}

	protected boolean match_domain(Configuration conf)
	{
		return conf.include.get("domain") == null || ((String)(conf.include.get("domain"))).equals(this.domain);
	}

	protected boolean exclude_match_domain(Configuration conf)
	{
		return conf.exclude.get("domain") != null && ((String)(conf.exclude.get("domain"))).equals(this.domain);
	}

	protected boolean exclude_match_bean(Configuration conf)
	{
		String bean = (String) conf.exclude.get("bean");
		String bean_name = (String) conf.exclude.get("bean_name");
		String[] excluded_bean_params = {"domain", "bean_name", "bean", "attribute"};


		if (this.bean_name.equals(bean) || this.bean_name.equals(bean_name))
		{
			return true;
		}

		for (String bean_attr: conf.exclude.keySet())
		{	
			if (Arrays.asList(excluded_bean_params).contains(bean_attr)) 
			{
				continue;
			}

			HashMap<String, String> bean_params = new HashMap<String, String>();
			for (String param : this.tags)
			{
				String[] param_split = param.split(":");
				bean_params.put(param_split[0], param_split[1]);
			}

			if(conf.exclude.get(bean_attr).equals(bean_params.get(bean_attr)))
			{
				return true;
			}



		}
		return false;

	}

	protected static String convert_metric_name(String metric_name)
	{
		String first_cap_pattern = "(.)([A-Z][a-z]+)";
		String all_cap_pattern = "([a-z0-9])([A-Z])";
		String metric_replacement = "([^a-zA-Z0-9_.]+)|(^[^a-zA-Z]+)";
		String dot_underscore = "_*\\._*";
		metric_name = metric_name.replaceAll(first_cap_pattern, "$1_$2");
		metric_name = metric_name.replaceAll(all_cap_pattern, "$1_$2").toLowerCase();
		metric_name = metric_name.replaceAll(metric_replacement, "_");
		metric_name = metric_name.replaceAll(dot_underscore, ".").trim();
		return metric_name;

	}

	protected boolean match_bean(Configuration conf)
	{
		String[] excluded_bean_params = {"domain", "bean_name", "bean", "attribute"};
		boolean match_bean_name = (conf.include.get("bean") == null && conf.include.get("bean_name") == null) || ((String)(conf.include.get("bean"))).equals(this.bean_name) || ((String)(conf.include.get("bean_name"))).equals(this.bean_name);

		if (!match_bean_name)
		{
			return false;
		}

		for (String bean_attr: conf.include.keySet())
		{	
			if (Arrays.asList(excluded_bean_params).contains(bean_attr)) 
			{
				continue;
			}

			HashMap<String, String> bean_params = new HashMap<String, String>();
			for (String param : this.tags)
			{
				String[] param_split = param.split(":");
				bean_params.put(param_split[0], param_split[1]);
			}

			if (bean_params.get(bean_attr) == null || !((String)(bean_params.get(bean_attr))).equals(((String)(conf.include.get(bean_attr)))))
			{
				return false;
			}


		}
		return true;
	}

}
