package org.datadog.jmxfetch;
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

    public void displayMetricReached() {
        throw new UnsupportedOperationException();      
    }

    public void displayMatchingAttributeName(JMXAttribute jmxAttribute, int rank, int limit) {
        throw new UnsupportedOperationException();      
    }

    public void displayNonMatchingAttributeName(JMXAttribute jmxAttribute) {
        throw new UnsupportedOperationException();      
    }

    @Override
    public void displayInstanceName(Instance instance) {
        throw new UnsupportedOperationException();      
    }

}
