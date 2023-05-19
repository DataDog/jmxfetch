[![Build Status](https://travis-ci.com/DataDog/jmxfetch.png?branch=master)](https://travis-ci.com/DataDog/jmxfetch)

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

JMXFetch uses [Maven](http://maven.apache.org) for its build system.

In order to generate the jar artifact, simply run the ```mvn clean compile assembly:single``` command in the cloned directory.

The distribution will be created under ```target/```.

Once the jar is created, you can update the one in the Datadog Agent repo.

## Coding standards

JMXFetch uses [Checkstyle](http://checkstyle.sourceforge.net/) with [Google Java Style](http://google.github.io/styleguide/javaguide.html) to enforce coding standards.

To perform a `Checkstyle` analysis and outputs violations, run:
```
mvn checkstyle::check
```

`Checkstyle` analysis is automatically executed prior to compiling the code, testing.

## Generated code

JMXFetch uses [Lombok](https://projectlombok.org/) to modify classes and generate additional code at runtime.
You may need to [enable annotation processors](https://projectlombok.org/setup/overview) to compile in your IDE.

## Testing

To run unit test, issue the following command:
```
mvn test
```

Some tests utilize [TestContainers](https://www.testcontainers.org/) which requires a docker client.
You can rely on the CI to run these, or if you have docker installed on Linux these should work out of the box.

If you're on macOS or Windows, docker desktop is architected to run a linux VM which then runs all your containers.
This makes the networking a bit different and you should use the following command to run the tests.

```
docker run -it --rm -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -v $PWD:$PWD -w $PWD -v /var/run/docker.sock:/var/run/docker.sock maven:3.8-eclipse-temurin-8 mvn test
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
mvn -DskipTests -DskipStaging=true -DperformRelease=true --settings settings.xml clean deploy
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
java -jar jmxfetch-0.47.9-SNAPSHOT-jar-with-dependencies.jar --help
```
