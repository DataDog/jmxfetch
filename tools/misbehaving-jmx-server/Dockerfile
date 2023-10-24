# Use the official JDK image as the base image
FROM eclipse-temurin:17 AS base

# Use the base image as the build image
FROM base AS build
# Set the working directory to /app
WORKDIR /app

# Copy the pom.xml and Maven files and install the dependencies
COPY .mvn .mvn/
COPY pom.xml mvnw mvnw.cmd ./
RUN set -eu &&  \
    ./mvnw dependency:resolve;

# Copy the source code and build the JAR file
COPY src/ src/
RUN set -eu &&  \
    ./mvnw clean package assembly:single;

# Use the base image as the the final image
FROM base AS final

# Set the working directory to /app
WORKDIR /app

# Copy the JAR file from the Maven image to the final image
COPY  --from=build /app/target/misbehavingjmxserver-1.0-SNAPSHOT-jar-with-dependencies.jar .

# Run the supervisor class from the jar
CMD ["java", "-cp", "misbehavingjmxserver-1.0-SNAPSHOT-jar-with-dependencies.jar", "org.datadog.supervisor.App"]