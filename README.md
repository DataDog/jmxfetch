[![Build Status](https://github.com/DataDog/jmxfetch/actions/workflows/test.yml/badge.svg)](https://github.com/DataDog/jmxfetch/actions/workflows/test.yml)

# [Change log](https://github.com/DataDog/jmxfetch/blob/master/CHANGELOG.md)

# JMXFetch

JMXFetch is the new tool to collect metrics from JMX Servers in order to be sent to Datadog (http://www.datadoghq.com)
It is called by the Datadog Agent (https://github.com/Datadog/datadog-agent) and sends metrics back to the Agent using the [Java `dogstatsd` library](https://github.com/datadog/java-dogstatsd-client).

## How to contribute code?

First of all and most importantly, **thank you** for sharing.

If you want to submit code, please fork this repository and submit pull requests against the `master` branch.

Please note that the Agent is licensed for simplicity's sake
under a simplified BSD license, as indicated in the `LICENSE` file.
Exceptions are marked with LICENSE-xxx where xxx is the component name.
If you do **not** agree with the licensing terms and wish to contribute code nonetheless,
please email us at <info@datadoghq.com> before submitting your
pull request.

## Building from source

JMXFetch uses [Maven](http://maven.apache.org) for its build system. The repo contains a [Maven Wrapper](https://maven.apache.org/wrapper/), so you don't need to download and install Maven.

In order to generate the JAR artifact, run the `./mvnw clean compile assembly:single` command in the cloned directory.

The distribution will be created under ```target/```.

To use this JAR in the Agent, see [these docs](https://github.com/DataDog/datadog-agent/blob/main/docs/dev/checks/jmxfetch.md).

To test your JAR with a local test-server, see below instructions for "Local Testing"

### Note

If you want build all the JAR files for JMXFetch, you need to use an older JDK version like JDK 8.
There is a known issue where the build can't find `javadoc command` on modern JDKs.
The quickest way to build these JAR files is to use Docker:

```
docker run -it --rm \
    --name my-maven-project \
    -v "$(pwd)":/usr/src/app \
    -w /usr/src/app \
    eclipse-temurin:8-jdk ./mvnw -DskipTests clean package
```

## Coding standards

JMXFetch uses [Checkstyle](http://checkstyle.sourceforge.net/) with [Google Java Style](http://google.github.io/styleguide/javaguide.html) to enforce coding standards.

To perform a `Checkstyle` analysis and outputs violations, run:
```
./mvnw checkstyle::check
```

`Checkstyle` analysis is automatically executed prior to compiling the code, testing.

## Generated code

JMXFetch uses [Lombok](https://projectlombok.org/) to modify classes and generate additional code at runtime.
You may need to [enable annotation processors](https://projectlombok.org/setup/overview) to compile in your IDE.

## Useful Developer Settings

### JDK version management
[`sdkman`](https://sdkman.io/install) is recommended to manage multiple versions of Java.
If you are an sdkman user, there is a config file present in this project with
the recommended JDK version for development, use `sdk env` to activate it.


### Logging Options
The logging output is configured in code in `CustomLogger.java`.
The following system properties will affect the format of the log records.

- `-Djmxfetch.filelinelogging=true`
    - All log records will include the specific file and line number which emitted
      that log.
- `-Djmxfetch.millisecondlogging=true`
    - All log records will include milliseconds in the timestamp, rather than the default
      'second' timestamp resolution.

### Local Testing
You can utilize the provided testing server `misbehaving-jmx-server` with the
preset `./docker-compose.yaml` file. This runs two containers, one is the
test server and the other is the Datadog Agent running your local JAR's version
of JMXFetch.

1. `docker compose up -d`



## Testing

To run unit test, issue the following command:
```
./mvnw test
```

Some tests utilize [TestContainers](https://www.testcontainers.org/) which requires a docker client.
You can rely on the CI to run these, or if you have docker installed on Linux these should work out of the box.

If you're on macOS or Windows, docker desktop is architected to run a linux VM which then runs all your containers.
This makes the networking a bit different and you should use the following command to run the tests.

```
docker run -it --rm -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -v $PWD:$PWD -w $PWD -v /var/run/docker.sock:/var/run/docker.sock eclipse-temurin:8-jdk ./mvnw test
```

This version runs the maven jmxfetch tests within a container as well, which works as long as the `TEST_CONTAINERS_HOST_OVERRIDE` env var is set.

### Testing Deployments

You can test the deployment by using the Nexus3 OSS image. To do so you need to:

- Use the local endpoints in the `pom.xml`:
```xml
...
    <distributionManagement>
        <snapshotRepository>
          <id>nexus</id>
          <url>http://localhost:8081/repository/maven-snapshots/</url>
        </snapshotRepository>
        <repository>
            <id>nexus</id>
            <url>http://localhost:8081/repository/maven-releases/</url>
        </repository>
    </distributionManagement>
...
    <build>
        <plugins>
            ...
            <plugin>
                <groupId>org.sonatype.plugins</groupId>
                ...
                <configuration>
                    <serverId>nexus</serverId>
                    <nexusUrl>http://localhost:8081/repository/maven-releases/</nexusUrl>
                    <autoReleaseAfterClose>false</autoReleaseAfterClose>
                </configuration>
            </plugin>
        </plugins>
    </build>
...
```
- Start the Nexus 3 container:
```sh
docker run --rm -d \
  --name nexus3 \
  -p 8081:8081 \
  -e INSTALL4J_ADD_VM_PARAMS='-Xms512m -Xmx512m -XX:MaxDirectMemorySize=512m -Djava.util.prefs.userRoot=/nexus-data/javaprefs' \
  sonatype/nexus3
```
- Wait for the server to be up on http://localhost:8081
- Get the administrator password:
```sh
docker exec -it nexus3 cat /nexus-data/admin.password
```
- Export those values for the build:
```sh
export GPG_KEY="<Key ID for signing the artifact>"
export GPG_PASSPHRASE="<Password for the private key of $GPG_KEY>"
export SONATYPE_USER="admin"
export SONATYPE_PASS="<Password for Nexus admin from previous step>"
```
- Run the deploy with the appropriate `skipStaging` flag:
```sh
./mvnw -DskipTests -DskipStaging=true -DperformRelease=true --settings settings.xml clean deploy
```

If you do this correctly, the artifact will be available in the Nexus container at
http://localhost:8081/#browse/browse:maven-releases.

Note: If you are trying to redeploy the same version of the artifact to the local
Nexus repository, you will need to change the `Deployment Policy` for `maven-releases`
to `Allow redeploy` in the [UI](http://localhost:8081/#admin/repository/repositories:maven-releases)
otherwise the subsequent publishes will fail.

## Running:

```
Get help on usage:
java -jar jmxfetch-0.50.1-SNAPSHOT-jar-with-dependencies.jar --help
```

## Updating Maven Wrapper

To upgrade the Maven Wrapper, you need to run:

```
./mvn wrapper:wrapper -Dmaven=<Maven Version X.Y.Z>
```

The easiest way to regenerate the wrapper files (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.jar` and `.mvn/wrapper/maven-wrapper.properties`) is to use Docker:

```
docker run -it --rm \
    --name my-maven-project \
    -v "$(pwd)":/usr/src/app \
    -w /usr/src/app maven:3 mvn wrapper:wrapper -Dmaven=<Maven Version X.Y.Z>
```

Leave out the `-Dmaven=<Maven Version X.Y.Z>` to get the latest version of Maven.
