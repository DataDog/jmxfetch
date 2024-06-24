package org.datadog.jmxfetch;

import java.io.IOException;
import java.util.Map;

public interface ConnectionFactory {
    /** Factory method to create connections, both remote and local to the target JVM. */
    Connection createConnection(Map<String, Object> connectionParams) throws IOException;
}
