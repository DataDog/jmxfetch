package org.datadog.jmxfetch.reporter;

import com.timgroup.statsd.NonBlockingStatsDClientBuilder;
import com.timgroup.statsd.ServiceCheck;
import com.timgroup.statsd.StatsDClient;
import lombok.extern.slf4j.Slf4j;
import org.datadog.jmxfetch.Instance;
import org.datadog.jmxfetch.JmxAttribute;


/** A reporter class to submit metrics via statsd. */
@Slf4j
public class StatsdReporter extends Reporter {

    private StatsDClient statsDClient;
    private String statsdHost;
    private int statsdPort;
    private Boolean telemetry;
    private int queueSize;
    private long initializationTime;
    private boolean nonBlocking;
    private int socketBufferSize;
    private int socketTimeout;

    /** Constructor, instantiates statsd reported to provided host and port. */
    public StatsdReporter(String statsdHost, int statsdPort, boolean telemetry, int queueSize,
        boolean nonBlocking, int socketBufferSize, int socketTimeout
    ) {
        this.statsdHost = statsdHost;
        this.statsdPort = statsdPort;
        this.telemetry = telemetry;
        this.queueSize = queueSize;
        this.nonBlocking = nonBlocking;
        this.socketBufferSize = socketBufferSize;
        this.socketTimeout = socketTimeout;
        this.init();
    }

    private void init() {
        initializationTime = System.currentTimeMillis();

        // Only set the entityId to "none" if UDS communication is activated
        String entityId = this.statsdPort == 0 ? "none" : null;

        handler = new LoggingErrorHandler();
        /* Create the StatsDClient with "entity-id" set to "none" to avoid
           having dogstatsd server adding origin tags, when the connection is
           done with UDS. */
        log.info("Initializing Statsd reporter with parameters host={} port={} "
                        + "telemetry={} queueSize={} entityId={} blocking={} "
                        + "socketBufferSize={} socketTimeout={}",
                this.statsdHost, this.statsdPort, this.telemetry, this.queueSize, entityId,
                !this.nonBlocking, this.socketBufferSize, this.socketTimeout);
        NonBlockingStatsDClientBuilder builder = new NonBlockingStatsDClientBuilder()
                .hostname(this.statsdHost)
                .port(this.statsdPort)
                .enableTelemetry(this.telemetry)
                .queueSize(this.queueSize)
                .blocking(!nonBlocking)
                .errorHandler(handler)
                .entityID(entityId);

        // When using UDS set the datagram size to 8k and disable origin detection
        if (this.statsdPort == 0) {
            builder.maxPacketSizeBytes(8192);
            builder.constantTags("dd.internal.card:none");
        }
        if (this.socketBufferSize != 0) {
            builder.socketBufferSize(this.socketBufferSize);
        }
        if (this.socketTimeout != 0) {
            builder.timeout(this.socketTimeout);
        }
        statsDClient = builder.build();
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

    public boolean getTelemetry() {
        return telemetry;
    }

    public int getQueueSize() {
        return queueSize;
    }
}
