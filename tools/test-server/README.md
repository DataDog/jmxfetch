# Test server


## To build and run

### Docker

Build:

```shell
docker build -t jmx-test-server .
```

Run:

```shell
docker run --rm -it -p 9010:9010 jmx-test-server
```

Then use the following config:

```yaml
---
init_config:
  is_jmx: true
  collect_default_metrics: true

instances:
  - host: localhost
    port: 9010
    user: username
    name: jmx_instance_name
    conf:
      - include:
        domain: test
        type:
          - MyMbean

```

To test the config run:
```shell
java -jar jmxfetch-0.47.3-jar-with-dependencies.jar \
--reporter console \
--check example-jmx-fetch-config.yaml \
--log_level DEBUG \
collect
```

Using [jmxterm](https://docs.cyclopsgroup.org/jmxterm):

```shell
java -jar jmxterm-1.0.2-uber.jar -l service:jmx:rmi:///jndi/rmi://localhost:9010/jmxrmi
```

## Generating a new KeyStore and TrustStore

The following generates the KeyStore for the test-server:

```shell
keytool \
    -genkey \
    -keystore broker.ks \
    -keyalg RSA \
    -keysize 2048 \
    -alias broker \
    -validity 365000 \
    -dname "CN=JMXFetch,OU=AML,O=Datadog,L=NYC,ST=NYC,C=US"
```

Set password to `changeit`.

Next you need to export the cert:

```shell
keytool \
  -export \
  -keystore broker.ks \
  -alias broker \
  -file broker.cert
```

Next create the keystore for the client/JMXFetch:

```shell
keytool \
    -genkey \
    -keystore client.ks \
    -keyalg RSA \
    -keysize 2048 \
    -alias client \
    -validity 365000 \
    -dname "CN=JMXFetch,OU=AML,O=Datadog,L=NYC,ST=NYC,C=US"
```

Create a truststore for the client, and import the test server’s certificate.
This establishes that the client “trusts” the broker:

```shell
keytool \
  -import \
  -keystore client.ts \
  -file broker.cert \
  -alias broker
```
