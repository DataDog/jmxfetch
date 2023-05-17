# Misbehaving JMX Server
This project exists to have a JMX server that is easily controllable from an external process
to intentionally cause connection issues.

## Current features
- cut and restore the TCP network connections between the JMX server and any clients attached

## Implementation
`org.datadog.misbehavingjmxserver` is a JMX server exposing custom mbeans
as well as an HTTP control interface to allow injection of network errors.

## Build
`mvn clean compile assembly:single`

## Run
`java -jar target/misbehavingjmxserver-1.0-SNAPSHOT-jar-with-dependencies.jar`

## Configure
- `RMI_PORT` - RMI port for jmx failure server to use (default 1099)
- `RMI_HOST` - hostname for JMX to listen on (default localhost)
- `CONTROL_PORT` - HTTP control port (default 8080)

## HTTP Control Actions
- POST `/cutNetwork` - Denies any requests to create a new socket (ie, no more connections will be 'accept'ed) and then closes existing TCP sockets
- POST `/restoreNetwork` - Allows new sockets to be created
- GET `/beans/:domain` - Retrieves a list of bean names that are currently registered under the given domain. The length of this array should be exactly the number of beans under that domain
- POST `/beans/:domain` - Declares how many 4-attribute beans should exist with this domain. Beans will either be created or destroyed to reach the desired amount. Payload should be JSON with a single key: `numDesiredBeans`.

## Docker
```
$ docker build -t misbehaving-jmx-server .
$ docker run --rm -p :1099 misbehaving-jmx-server 
```

Can connect via jmxterm ` java -jar ~/jmxterm-1.0.2-uber.jar --url localhost:<rmi port>`

## Testing the server with the Agent

There are a couple of ways you can get the Agent to pull metrics from this test server.

### JMX integration config

Copy `misbehaving-jmxfetch-conf.yaml` to `/etc/datadog-agent/conf.d/` and just run the uber jar created by Maven.
You will need to restart the Agent to pick up the config.

### Using Docker

After building the `misbehaving-jmx-server` you can simply run:

```shell
$ docker run \
--rm \
-p 1099:1099 \
--label "com.datadoghq.ad.checks"='{"misbehaving":{"init_config":{"is_jmx":true},"instances":[{"host":"%%host%%","port":"1099","collect_default_jvm_metrics":false,"max_returned_metrics":300000,"conf":[{"include":{"domain":"Bohnanza"}}]}]}}' \
misbehaving-jmx-server 
```

The Agent will auto discover the container and begin to collect metrics from it. 

### Using Docker Compose

```shell
$ docker compose up
```

The Agent will auto discover the container and begin to collect metrics from it.
