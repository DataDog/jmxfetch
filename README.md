# JMXfetch

JMXFetch is the new tool to collect metrics from JMX Servers

# Building from Source

JMXC uses "Maven":http://maven.apache.org for its build system.

In order to create a distribution, simply run the ```mvn clean compile assembly:single``` command in the cloned directory.

The distribution will be created under ```target/```.

Once the jar is created, you can update the one in the ddagent repo.
