package org.datadog.jmxfetch.reporter;

import com.timgroup.statsd.*;
import lombok.extern.slf4j.Slf4j;
import org.datadog.jmxfetch.Instance;
import org.datadog.jmxfetch.JmxAttribute;

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

        // Only set the entityId to "none" if UDS communication is activated
        String entityId = this.statsdPort == 0 ? "none" : null;

        /* Create the StatsDClient with "entity-id" set to "none" to avoid
           having dogstatsd server adding origin tags, when the connection is
           done with UDS. */
        statsDClient = new NonBlockingStatsDClientBuilder()
                .enableTelemetry(false)
                .hostname(this.statsdHost)
                .port(this.statsdPort)
                .queueSize(Integer.MAX_VALUE)
                .errorHandler(new LoggingErrorHandler())
                .entityID(entityId)
                .build();
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

    /** Submits service check. */
    public void doSendServiceCheck(
            String serviceCheckName, String status, String message, String[] tags) {
        if (System.currentTimeMillis() - this.initializationTime > 300 * 1000) {
            this.statsDClient.stop();
            init();
        }

        ServiceCheck sc = ServiceCheck.builder()
                .withName(serviceCheckName)
                .withStatus(this.statusToServiceCheckStatus(status))
                .withMessage(message)
                .withTags(tags)
                .build();

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
