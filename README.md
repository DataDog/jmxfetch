# JMXfetch

JMXFetch is the new tool to collect metrics from JMX Servers

# Building from Source

JMXFetch uses "Maven":http://maven.apache.org for its build system.

In order to create a distribution, simply run the ```mvn clean compile assembly:single``` command in the cloned directory.

The distribution will be created under ```target/```.

Once the jar is created, you can update the one in the ddagent repo.

# To run:
java -jar jmxfetch-0.0.1-SNAPSHOT-jar-with-dependencies.jar PATH_TO_CONF_D_DIR STATSD_PORT CHECK_FREQUENCY_IN_MS PATH_TO_LOG_FILE LOG_LEVEL tomcat.yaml,activemq.yaml,solr.yaml,cassandra.yaml,jmx.yaml
