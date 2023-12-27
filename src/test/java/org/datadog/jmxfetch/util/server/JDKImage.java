package org.datadog.jmxfetch.util.server;

public enum JDKImage {
    BASE("base"),
    JDK_11("eclipse-temurin:11"),
    JDK_17("eclipse-temurin:17"),
    JDK_21("eclipse-temurin:21");

    private final String image;

    private JDKImage(final String image) {
        this.image = image;
    }

    @Override
    public String toString() {
        return this.image;
    }
}
