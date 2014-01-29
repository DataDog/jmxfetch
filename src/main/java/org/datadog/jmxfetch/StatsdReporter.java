package org.datadog.jmxfetch;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;

public class StatsdReporter extends Reporter{

    private final StatsDClient STATSD_CLIENT;

    public StatsdReporter(int statsd_port) {
        this.STATSD_CLIENT = new NonBlockingStatsDClient(null, "localhost", statsd_port, new String[] {});      
    }

    protected void _sendMetricPoint(String metricName, double value, String[] tags) {
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
