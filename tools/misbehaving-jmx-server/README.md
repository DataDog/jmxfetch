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

## Docker
```
$ docker build -t misbehaving-jmx-server .
$ docker run --rm -p :1099 misbehaving-jmx-server 
```

Can connect via jmxterm ` java -jar ~/jmxterm-1.0.2-uber.jar --url localhost:<rmi port>`
