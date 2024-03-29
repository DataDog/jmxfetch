# syntax=docker/dockerfile:1
# Use by default the JDK image used to build the jar
ARG FINAL_JRE_IMAGE=base
# Use the official JDK image as the base image
FROM eclipse-temurin:17 AS base

# Use the base image as the build image
FROM base AS build
# Set the working directory to /app
WORKDIR /app

# Copy the pom.xml and Maven files and install the dependencies
COPY .mvn .mvn/
COPY pom.xml mvnw mvnw.cmd ./

# TODO: investigate why mount caching does not seem to work Test containers
# Enabling this will speed up tests as the Maven cache can be shared between all builds
# RUN --mount=type=cache,id=mavenCache,target=/root/.m2,sharing=locked \
RUN set -eu &&  \
    ./mvnw dependency:resolve;

# Copy the source code and build the JAR file
COPY src/ src/

# TODO: investigate why mount caching does not seem to work Test containers
# RUN --mount=type=cache,id=mavenCache,target=/root/.m2,sharing=locked \
RUN set -eu &&  \
    ./mvnw clean package assembly:single;

# Use the image specified by FINAL_JRE_IMAGE build arg (default "base")
FROM ${FINAL_JRE_IMAGE} AS final

# Set the working directory to /app
WORKDIR /app

COPY scripts/start.sh /usr/bin/

# Copy the JAR file from the Maven image to the final image
COPY  --from=build /app/target/misbehavingjmxserver-1.0-SNAPSHOT-jar-with-dependencies.jar .

# RMI Port
EXPOSE 9090

# Control Port
EXPOSE 9091

# Supervisor Port
EXPOSE 9092

# Run the supervisor class from the jar
ENTRYPOINT [ "/usr/bin/start.sh" ]

CMD [ "org.datadog.supervisor.App" ]
