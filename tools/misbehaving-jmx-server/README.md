# Misbehaving JMX Server
This project exists to have a JMX server that is easily controllable from an external process
to intentionally cause connection issues.

## Current features
- cut and restore the TCP network connections between the JMX server and any clients attached

## Implementation
- `org.datadog.misbehavingjmxserver.App` is a JMX server exposing custom mbeans
as well as an HTTP control interface to allow injection of network errors. Entrypoint class for the jar.
- `org.datadog.supervisor.App` is a secondary class (non-entrypoint) for the JAR and its job is to wait for
a secondary `init` payload that contains the correct RMI Hostname. It is designed for use in a container where you may not know the hostname before starting the container.

## Build
`mvn clean compile assembly:single`

## Run standalone JMX server
`java -jar target/misbehavingjmxserver-1.0-SNAPSHOT-jar-with-dependencies.jar`

## Run with supervisor
`java -cp target/misbehavingjmxserver-1.0-SNAPSHOT-jar-with-dependencies.jar org.datadog.supervisor.App`

## Configure
- `RMI_PORT` - RMI port for jmx failure server to use (default 1099)
- `RMI_HOST` - hostname for JMX to listen on (default localhost)
- `CONTROL_PORT` - HTTP control port (default 8080)
- `SUPERVISOR_PORT` - HTTP control port for the supervisor process (if using) (default 8088)
- `MISBEHAVING_OPTS` - Manages memory, GC configurations, and system properties of the Java process running the JMXServer (default `-Xmx128M -Xms128M`)

## HTTP Control Actions (jmx-server)
- POST `/cutNetwork` - Denies any requests to create a new socket (ie, no more connections will be 'accept'ed) and then closes existing TCP sockets
- POST `/restoreNetwork` - Allows new sockets to be created
- GET `/beans/:domain` - Retrieves a list of bean names that are currently registered under the given domain. The length of this array should be exactly the number of beans under that domain
- POST `/beans/:domain` - Declares how many beans should exist with this domain. Beans are either created or destroyed to reach the desired amount. Payload should be JSON with four keys: `beanCount`,`scalarAttributeCount`,`tabularAttributeCount`,`compositeValuesPerTabularAttribute`.

## Bean Configuration
- `beanCount` - Declares how many beans should be present in a specfic domain
- `scalarAttributeCount` - Defines the number of simple attributes in all beans for a given domain
- `tabularAttributeCount` - Defines the number of tabular attributes in each bean for a given domain
- `compositeValuesPerTabularAttribute` - Defines the number of rows of data per tabular attribute
Beans in a given domain must all have the same structure, so updating these values with the HTTP Control Server erases all beans and recreates them to the set beanCount with the same number of attributes per bean.

## Configuration File
Using the command line options `--config-path` or `-cfp` you can provide a path to a YAML configuration file to create beans automatically upon the start of misbehaving-jmx-server.
An example file can be found at `misbehaving-jmx-domains-config.yaml`.


## HTTP Control Actions (supervisor)
- POST `/init` - Provides `rmiHostname` to be used by jmx-server. jmx server will not be listening until this init payload is sent

## Docker
```
$ docker build -t misbehaving-jmx-server .
$ docker run --rm -p :1099 misbehaving-jmx-server
```

Can connect via jmxterm ` java -jar ~/jmxterm-1.0.2-uber.jar --url localhost:<rmi port>`

## Testing the server with the Agent

There are a couple of ways you can get the Agent to pull metrics from this test server.

### JMX integration config

Copy `misbehaving-jmxfetch-conf.yaml` to `/etc/datadog-agent/conf.d/` and just run the `with-dependencies` jar created by Maven.
You will need to restart the Agent to pick up the config.

### Using Docker Compose

```shell
$ docker compose up
```

The Agent will auto-discover the container and begin to collect metrics from it.

### Using Docker

If your container's IP is directly
accessible by your Agent, you can use the following `run` command and use AD.

```shell
$ docker run \
--rm \
-p 1099:1099 \
--label "com.datadoghq.ad.checks"='{"misbehaving":{"init_config":{"is_jmx":true},"instances":[{"host":"%%host%%","port":"1099","collect_default_jvm_metrics":false,"max_returned_metrics":300000,"conf":[{"include":{"domain":"Bohnanza"}}]}]}}' \
misbehaving-jmx-server
```

The Agent will auto discover the container and begin to collect metrics from it.

Note that this implicitly sets the `RMI_HOSTNAME` to `localhost` which is where
the host port mapping comes into play. If this is giving you trouble, consider
using the [docker-compose setup](#using-docker-compose).

