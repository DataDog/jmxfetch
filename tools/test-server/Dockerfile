# syntax=docker/dockerfile:1
ARG JDK_VERSION=17
FROM eclipse-temurin:${JDK_VERSION}-jdk AS build

WORKDIR /src/app

COPY src .

WORKDIR /src/app/main/java

RUN set -eu && \
    javac -d . com/datadoghq/jmxfetch/test/Service.java ;

FROM eclipse-temurin:${JDK_VERSION}-jre AS final

COPY --from=build /src /

WORKDIR /app/main/java

EXPOSE 9010

ENTRYPOINT [ \
    "java", \
    "-Dcom.sun.management.jmxremote=true", \
    "-Dcom.sun.management.jmxremote.port=9010", \
    "-Dcom.sun.management.jmxremote.rmi.port=9010", \
    "-Dcom.sun.management.jmxremote.authenticate=false", \
    "-Dcom.sun.management.jmxremote.ssl=false", \
    "com.datadoghq.jmxfetch.test.Service" \
]

CMD [ \
    "10", \
    "true" \
]
