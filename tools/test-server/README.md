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

Using [jmxterm](https://docs.cyclopsgroup.org/jmxterm):

```shell
java -jar jmxterm-1.0.2-uber.jar -l service:jmx:rmi:///jndi/rmi://localhost:9010/jmxrmi
```
