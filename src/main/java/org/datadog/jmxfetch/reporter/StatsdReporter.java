package org.datadog.jmxfetch.reporter;

import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.ServiceCheck;
import com.timgroup.statsd.StatsDClient;
import com.timgroup.statsd.StatsDClientErrorHandler;
import lombok.extern.slf4j.Slf4j;
import org.datadog.jmxfetch.Instance;
import org.datadog.jmxfetch.JmxAttribute;
import org.datadog.jmxfetch.Status;

/** A reporter class to submit metrics via statsd. */
@Slf4j
public class StatsdReporter extends Reporter {

    private StatsDClient statsDClient;
    private String statsdHost;
    private int statsdPort;
    private long initializationTime;

    private class LoggingErrorHandler implements StatsDClientErrorHandler {

        @Override
        public void handle(Exception exception) {
            log.error("statsd client error:", exception);
        }
    }

    /** Constructor, instantiates statsd reported to provided host and port. */
    public StatsdReporter(String statsdHost, int statsdPort) {
        this.statsdHost = statsdHost;
        this.statsdPort = statsdPort;
        this.init();
    }

    private void init() {
        initializationTime = System.currentTimeMillis();
        statsDClient =
                new NonBlockingStatsDClient(
                        null, this.statsdHost, this.statsdPort, new String[] {});
    }

    protected void sendMetricPoint(
            String metricType, String metricName, double value, String[] tags) {
        if (System.currentTimeMillis() - this.initializationTime > 300 * 1000) {
            this.statsDClient.stop();
            init();
        }
        if (metricType.equals("monotonic_count")) {
            statsDClient.count(metricName, (long) value, tags);
        } else if (metricType.equals("histogram")) {
            statsDClient.histogram(metricName, value, tags);
        } else {
            statsDClient.gauge(metricName, value, tags);
        }
    }

    private int statusToInt(String status) {
        if (status == Status.STATUS_OK) {
            return 0;
        } else if (status == Status.STATUS_WARNING) {
            return 1;
        } else if (status == Status.STATUS_ERROR) {
            // critical
            return 2;
        }
        return 3;
    }

    /** Submits service check. */
    public void doSendServiceCheck(String checkName, String status, String message, String[] tags) {
        if (System.currentTimeMillis() - this.initializationTime > 300 * 1000) {
            this.statsDClient.stop();
            init();
        }

        ServiceCheck sc =
                new ServiceCheck(
                        String.format("%s.can_connect", checkName),
                        this.statusToInt(status),
                        message,
                        tags);
        statsDClient.serviceCheck(sc);
    }

    public void displayMetricReached() {
        throw new UnsupportedOperationException();
    }

    public void displayMatchingAttributeName(JmxAttribute jmxAttribute, int rank, int limit) {
        throw new UnsupportedOperationException();
    }

    public void displayNonMatchingAttributeName(JmxAttribute jmxAttribute) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void displayInstanceName(Instance instance) {
        throw new UnsupportedOperationException();
    }

    public String getStatsdHost() {
        return statsdHost;
    }

    public int getStatsdPort() {
        return statsdPort;
    }
}
