package org.datadog.jmxfetch;
import javax.management.ObjectName;

import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;

public class StatsdMetricReporter extends MetricReporter{
    
    private final StatsDClient STATSD_CLIENT;
    
    public StatsdMetricReporter(int statsd_port) {
        this.STATSD_CLIENT = new NonBlockingStatsDClient(null, "localhost", statsd_port, new String[] {});      
    }
    
    protected void _sendMetricPoint(String metricName, double value, String[] tags) {
        STATSD_CLIENT.gauge(metricName, value, tags);
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
    
}
