# JMXFetch Development Guide for AI Agents

This guide provides essential information for AI coding agents working on the JMXFetch codebase.

## Build & Test Commands

### Building
```bash
# Clean build with JAR assembly
./mvnw clean compile assembly:single

# Quick build without tests (for JDK 8)
./mvnw clean package -DskipTests

# Full build with Docker (for compatibility with all JARs)
docker run -it --rm -v "$(pwd)":/usr/src/app -w /usr/src/app eclipse-temurin:8-jdk ./mvnw -DskipTests clean package
```

### Testing
```bash
# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=TestApp

# Run a specific test method
./mvnw test -Dtest=TestApp#testBeanTags

# Run tests with verbose logging
./mvnw test -Dtests.log_level=info

# Run tests on macOS/Windows with Docker (for TestContainers compatibility)
docker run -it --rm -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -v $PWD:$PWD -w $PWD -v /var/run/docker.sock:/var/run/docker.sock \
  eclipse-temurin:8-jdk ./mvnw test
```

### Linting & Code Quality
```bash
# Run Checkstyle analysis
./mvnw checkstyle:check

# Verify without running tests
./mvnw verify -DskipTests

# Skip Checkstyle (not recommended)
./mvnw test -Dcheckstyle.skip=true
```

## Code Style Guidelines

JMXFetch follows **Google Java Style** with customizations defined in `style.xml`.

### Formatting Rules

- **Indentation**: 4 spaces (NO tabs)
- **Line length**: 100 characters max (exceptions for imports and URLs)
- **Braces**: Always required, even for single-line if/for/while statements
- **One statement per line**: No multiple statements on the same line
- **Empty blocks**: Use `{}` or provide a TEXT comment

### Import Organization

Imports MUST be organized in this order (enforced by Checkstyle):
1. **Static imports** (e.g., `import static org.junit.Assert.assertEquals;`)
2. **Special imports** (e.g., `com.google`)
3. **Third-party packages** (e.g., `org.datadog`, `lombok`, `org.yaml`)
4. **Standard Java packages** (e.g., `java.io`, `java.util`, `javax.management`)

Within each group, imports are **alphabetically sorted**.

**NO star imports** - always use specific imports (e.g., `import java.util.List;` not `import java.util.*;`)

Example import block:
```java
package org.datadog.jmxfetch;

import static org.junit.Assert.assertEquals;

import lombok.extern.slf4j.Slf4j;

import org.datadog.jmxfetch.reporter.Reporter;
import org.datadog.jmxfetch.util.CustomLogger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.management.ObjectName;
```

### Naming Conventions

- **Classes/Interfaces**: PascalCase (e.g., `AppConfig`, `TaskProcessor`)
- **Methods**: camelCase, minimum 3 characters (e.g., `addInstanceStats`, `flush`)
- **Variables/Parameters**: camelCase, minimum 3 characters (e.g., `instanceStats`, `checkName`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `STATUS_WARNING`, `MAX_RETURNED_METRICS`)
- **Packages**: lowercase, no underscores (e.g., `org.datadog.jmxfetch.tasks`)
- **Type parameters**: Single capital letter or PascalCase ending in T (e.g., `T`, `ValueT`)

### Types & Annotations

- **Lombok**: Used extensively for reducing boilerplate
  - `@Slf4j` for logging (provides `log` field)
  - `@Builder` for builder pattern (use in AppConfig and similar)
  - Enable annotation processors in your IDE
- **Suppress Warnings**: Use `@SuppressWarnings("unchecked")` when necessary for type casts
- **Nullability**: No explicit annotations; use defensive null checks

### Documentation

- **Public methods**: Should have Javadoc (minimum 2 lines to require Javadoc)
- **Javadoc style**: 
  - Start with `/**`, end with `*/`
  - Order: `@param`, `@return`, `@throws`, `@deprecated`
  - Summary sentence should not start with "This method returns" or "@return the"
- **Test methods**: Use `@Test` annotation, Javadoc not required

### Logging

Use SLF4J via Lombok's `@Slf4j` annotation:

```java
@Slf4j
public class MyClass {
    public void myMethod() {
        log.debug("Debug message: {}", value);
        log.info("Info message");
        log.warn("Warning message: {}", exception.getMessage());
        log.error("Error occurred", exception);
    }
}
```

**Logging levels**:
- `log.debug()`: Detailed debugging information
- `log.info()`: General informational messages
- `log.warn()`: Warning conditions, recoverable errors
- `log.error()`: Error conditions, exceptions

### Error Handling

- **Custom Exceptions**: Extend `Exception` (e.g., `TaskProcessException`)
- **Exception naming**: End with "Exception" (e.g., `JsonException`)
- **Catch blocks**: Empty catch blocks ONLY allowed with variable name `expected`
- **Logging exceptions**: Include exception object in log calls for stack traces

Example:
```java
try {
    processData();
} catch (IOException e) {
    log.error("Failed to process data: {}", e.getMessage(), e);
    throw new TaskProcessException("Data processing failed");
}
```

## Testing Patterns

- **Framework**: JUnit 4 (not JUnit 5)
- **Test class naming**: Prefix with `Test` (e.g., `TestApp`, `TestCommon`)
- **Test method naming**: Prefix with `test` (e.g., `testBeanTags`, `testBeanRegexTags`)
- **Assertions**: Use static imports from `org.junit.Assert`
- **Mocking**: Use Mockito (`spy()`, `when()`, `verify()`)
- **Test base**: Extend `TestCommon` for JMX-related tests
- **Setup**: Use `@BeforeClass` for class-level setup, `@After` for cleanup
- **TestContainers**: Some tests use Docker containers (requires Docker daemon)

## Common Patterns

### File References
When referencing code locations in messages or logs, use the format:
```
src/main/java/org/datadog/jmxfetch/Instance.java:142
```

### Constants
Define constants at the top of the class:
```java
public class MyClass {
    private static final String STATUS_OK = "OK";
    private static final int MAX_RETRIES = 3;
```

### Thread Safety
Use `ConcurrentHashMap` for shared mutable state across threads.

## Project Structure

```
src/main/java/org/datadog/jmxfetch/
├── App.java              # Main application entry point
├── Instance.java         # JMX instance management
├── Configuration.java    # Configuration parsing
├── reporter/             # Metric reporters
├── tasks/                # Task processing framework
├── util/                 # Utility classes
└── validator/            # Validation logic

src/test/java/org/datadog/jmxfetch/
└── Test*.java            # Test classes
```

## Development Environment

- **JDK**: Use JDK 8 for development (JDK 7-24 supported for runtime)
- **JDK Management**: Use `sdkman` - run `sdk env` to activate project JDK
- **IDE Setup**: Enable annotation processors for Lombok support
- **Maven Wrapper**: Always use `./mvnw` instead of system Maven

## Important Notes

- Target compatibility is Java 1.7, so avoid Java 8+ language features
- Checkstyle runs automatically before compilation
- All PRs must pass CI tests on multiple JDK versions (8, 11, 17, 21, 24)
- Status file location: Test output goes to `/tmp/jmxfetch_test.log`
