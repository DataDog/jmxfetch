[![Build Status](https://travis-ci.com/DataDog/jmxfetch.png?branch=master)](https://travis-ci.com/DataDog/jmxfetch)

# [Change log](https://github.com/DataDog/jmxfetch/blob/master/CHANGELOG.md)

# JMXFetch

JMXFetch is the new tool to collect metrics from JMX Servers in order to be sent to Datadog (http://www.datadoghq.com)
It is called by the Datadog Agent (https://github.com/Datadog/datadog-agent) and sends metrics back to the Agent using the [Java `dogstatsd` library](https://github.com/datadog/java-dogstatsd-client).

# How to contribute code?

First of all and most importantly, **thank you** for sharing.

If you want to submit code, please fork this repository and submit pull requests against the `master` branch.

Please note that the Agent is licensed for simplicity's sake
under a simplified BSD license, as indicated in the `LICENSE` file.
Exceptions are marked with LICENSE-xxx where xxx is the component name.
If you do **not** agree with the licensing terms and wish to contribute code nonetheless,
please email us at <info@datadoghq.com> before submitting your
pull request.

# Building from source

JMXFetch uses [Maven](http://maven.apache.org) for its build system.

In order to generate the jar artifact, simply run the ```mvn clean compile assembly:single``` command in the cloned directory.

The distribution will be created under ```target/```.

Once the jar is created, you can update the one in the Datadog Agent repo.

# Coding standards

JMXFetch uses [Checkstyle](http://checkstyle.sourceforge.net/) with [Google Java Style](http://google.github.io/styleguide/javaguide.html) to enforce coding standards.

To perform a `Checkstyle` analysis and outputs violations, run:
```
mvn checkstyle::check
```

`Checkstyle` analysis is automatically executed prior to compiling the code, testing.

# Generated code

JMXFetch uses [Lombok](https://projectlombok.org/) to modify classes and generate additional code at runtime.
You may need to [enable annotation processors](https://projectlombok.org/setup/overview) to compile in your IDE.

# Testing

To run unit test, issue the following command:
```
mvn test
```

# To run:
```
Get help on usage:
java -jar jmxfetch-0.36.1-jar-with-dependencies.jar --help
```
