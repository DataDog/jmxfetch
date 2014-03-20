package org.datadog.jmxfetch;
import java.util.LinkedList;

import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;

public class StatsdReporter extends Reporter{

    private StatsDClient STATSD_CLIENT;
    private int statsd_port;
    private long initializationTime;

    public StatsdReporter(int statsd_port) {
        this.statsd_port = statsd_port;
        this.init();
    }
    
    private void init() {
        this.initializationTime = System.currentTimeMillis();
        this.STATSD_CLIENT = new NonBlockingStatsDClient(null, "localhost", this.statsd_port, new String[] {});  
    }

    protected void _sendMetricPoint(String metricName, double value, String[] tags) {
        if (System.currentTimeMillis() - this.initializationTime > 300 * 1000) {
            this.STATSD_CLIENT.stop();
            init();
        }
        STATSD_CLIENT.gauge(metricName, value, tags);
    }

  
    @Override
    public void displaySummary(int maxReturned, String instanceName, 
            String action, LinkedList<JMXAttribute> collectedAttributes, LinkedList<JMXAttribute> limitedAttributes,
            LinkedList<JMXAttribute> nonMatchingAttributes) {
        
    }

}
