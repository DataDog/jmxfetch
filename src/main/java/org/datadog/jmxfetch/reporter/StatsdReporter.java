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
        // Only set the entityId to "none" if UDS communication is activated
        String entityId = this.statsdPort == 0 ? "none" : null;
        int defaultUdsDatagramSize = 8192;

        handler = new LoggingErrorHandler();
        /* Create the StatsDClient with "entity-id" set to "none" to avoid
           having dogstatsd server adding origin tags, when the connection is
           done with UDS. */
        NonBlockingStatsDClientBuilder builder = new NonBlockingStatsDClientBuilder()
                .hostname(this.statsdHost)
                .port(this.statsdPort)
                .enableTelemetry(this.telemetry)
                .queueSize(this.queueSize)
                .blocking(!nonBlocking)
                .errorHandler(handler)
                .entityID(entityId);

        String extraInitLog = "";
        if (this.statsdPort == 0) {
            int packetSize = defaultUdsDatagramSize;

            // If a socketBufferSize is specified to be smaller than the default
            // uds datagram size, then we cannot create packets bigger than the
            // socket buffer size, otherwise we see `java.io.IOException: Message too long`
            //
            // Conversely, we cannot always make the packetSize equal to the user-specified
            // socketBufferSize. The user may specify a value larger than what is actually
            // allowed on their system, which would _also_ cause `Message too long` exceptions.
            //
            // In an ideal world, the maxPacketSize would be equal to the actual value of
            // socket send buffer at runtime. This logic would need to be implemented
            // inside java-dogstatsd-client.
            if (this.socketBufferSize != 0 && this.socketBufferSize < packetSize) {
                packetSize = this.socketBufferSize;
            }

            extraInitLog += "maxPacketSize=" + packetSize;
            builder.maxPacketSizeBytes(packetSize);
            // Disable origin detection
            builder.constantTags("dd.internal.card:none");
        }

        if (this.socketBufferSize != 0) {
            extraInitLog += " socketBufferSize=" + this.socketBufferSize;
            builder.socketBufferSize(this.socketBufferSize);
        }
        if (this.socketTimeout != 0) {
            extraInitLog += " socketTimeout=" + this.socketTimeout;
            builder.timeout(this.socketTimeout);
        }
        log.info("Initializing Statsd reporter with parameters host={} port={} "
                        + "telemetry={} queueSize={} entityId={} blocking={} "
                        + "{}",
                this.statsdHost, this.statsdPort, this.telemetry, this.queueSize, entityId,
                !this.nonBlocking, extraInitLog);
        statsDClient = builder.build();
    }

    protected void sendMetricPoint(
            String metricType, String metricName, double value, String[] tags) {
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
