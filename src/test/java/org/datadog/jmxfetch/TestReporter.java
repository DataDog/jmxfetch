package org.datadog.jmxfetch;

import java.util.HashMap;
import java.util.LinkedList;

import javax.management.ObjectName;

public class TestReporter extends Reporter{
    
    private LinkedList<HashMap<String, Object>> metrics = new LinkedList<HashMap<String, Object>>();

    @Override
    protected void _sendMetricPoint(String metricName, double value,
            String[] tags) {
        HashMap<String, Object> m = new HashMap<String, Object>();
        
        m.put("name", metricName);
        m.put("value", value);
        m.put("tags", tags);
        metrics.add(m);
    }
    
    public LinkedList<HashMap<String, Object>> getMetrics() {
        LinkedList<HashMap<String, Object>> returned_metrics = metrics;
        metrics = new LinkedList<HashMap<String, Object>>();
        return returned_metrics;
    }

    public void displayBeanName(ObjectName beanName) {
		throw new UnsupportedOperationException();
	}

	public void displayMetricReached() {
		throw new UnsupportedOperationException();		
	}

	public void displayMatchingAttributeName(JMXAttribute jmxAttribute, int rank) {
		throw new UnsupportedOperationException();		
	}

	public void displayNonMatchingAttributeName(JMXAttribute jmxAttribute) {
		throw new UnsupportedOperationException();		
	}
	
	public void displayInstanceName(Instance instance) {
		throw new UnsupportedOperationException();		
	}
    
}
