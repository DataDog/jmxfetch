# Misbehaving JMX Server

This project exists to have a JMX server that is easily controllable from an external process
to intentionally cause connection issues.

## Current features
- start/stop/restart the JMX server at a process level
- cut and restore the TCP network connections between the JMX server and any clients attached

## Implementation
There are two main pieces
- `org.datadog.misbehavingjmxserver` which is a JMX server exposing custom mbeans
   as well as a HTTP control interface to allow injection of network errors.
- `org.datadog.supervisor` which starts the misbehavingjmxserver and allows for it to be
  started/stopped/restarted on command. Allows for configuration of the misbehavingjmxserver as well.

Having a supervisor process run and monitor the actual jmxserver allows us to easily
test shutdown/startup of the jmxserver while being pid 1 in a container.

## Build
`mvn clean compile assembly:single`

## Run
`java -jar target/misbehavingjmxserver-1.0-SNAPSHOT-jar-with-dependencies.jar`

## Configure
- `JJ_RMI_PORT` - RMI port for jmx failure server to use
- `JJ_RMI_INTERFACE` - network interface for JMX to listen on
- `JJ_CONTROL_HTTP_PORT` - control port for supervisor process

## HTTP Interface
- POST `/start` - starts the misbehavingjmxserver if its not running
- POST `/stop` - stops the misbehavingjmxserver if it is running
- POST `/restart` - stops then starts the misbehavingjmxserver 
- POST `/jmx/closeAllSockets` - instructs the misbehavingjmxserver to abruptly close all open RMI tcp sockets
- POST `/jmx/cutNetwork` - Denies any requests to create a new socket (ie, no more connections will be 'accept'ed) and then closes existing TCP sockets
- POST `/jmx/restoreNetwork` - Allows new sockets to be created

> There are technically 2 control servers at play here, one in `misbehavingjmxserver` and one in the `supervisor`.
> The `misbehavingjmxserver` http interface is used by the supervisor and exposed as `/jmx/*`

## Docker
```
$ docker build -t misbehaving-jmx-server .
$ docker run --rm -p :1099 misbehaving-jmx-server 
```

Can connect via jmxterm ` java -jar ~/jmxterm-1.0.2-uber.jar --url localhost:<rmi port>`
