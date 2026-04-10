---
name: add-gc-test
description: Add integration tests to TestGCMetrics.java for new GC collector support added to new-gc-default-jmx-metrics.yaml. Use when a PR adds a new garbage collector to the metrics YAML without a corresponding test.
allowed-tools: Read Edit Grep Bash(git diff *) Bash(git log *) Bash(./mvnw *)
argument-hint: [branch-or-commit]
---

You are helping add an integration test to `TestGCMetrics.java` for new GC collector support.

## Changes Introducing New GC Collectors

```diff
!`git diff master -- src/main/resources/org/datadog/jmxfetch/new-gc-default-jmx-metrics.yaml 2>/dev/null || git diff HEAD~1 -- src/main/resources/org/datadog/jmxfetch/new-gc-default-jmx-metrics.yaml 2>/dev/null || echo "(no diff found — check the branch or commit manually)"`
```

$ARGUMENTS

## Your Task

1. **Read** `src/main/resources/org/datadog/jmxfetch/new-gc-default-jmx-metrics.yaml` — identify which new collector `name:` entries were added and what `alias:` metrics they map to.

2. **Read** `src/test/java/org/datadog/jmxfetch/TestGCMetrics.java` — understand existing test patterns and pick up on the `startAndGetMetrics` helper and both `assertGCMetric` overloads.

3. **Read** `src/test/java/org/datadog/jmxfetch/util/server/JDKImage.java` — confirm available JDK image constants (`JDK_8`, `JDK_11`, `JDK_11_OPENJ9`, `JDK_17`, `JDK_21`).

4. **Add** a new `@Test` method to `TestGCMetrics.java` following the patterns below.

## Patterns

### Standard GC with paired minor + major collectors
```java
@Test
public void testDefaultNewGCMetricsUse<GCName>() throws IOException {
    try (final MisbehavingJMXServer server = new MisbehavingJMXServer.Builder().withJDKImage(
        <JDK_IMAGE>).appendJavaOpts("<-XX:+UseXxxGC>").build()) {
        final List<Map<String, Object>> actualMetrics = startAndGetMetrics(server, true);
        assertThat(actualMetrics, hasSize(13));
        assertGCMetric(actualMetrics,
            "jvm.gc.minor_collection_count", "<YoungCollectorName>", "counter");
        assertGCMetric(actualMetrics,
            "jvm.gc.minor_collection_time", "<YoungCollectorName>", "counter");
        assertGCMetric(actualMetrics,
            "jvm.gc.major_collection_count", "<OldCollectorName>", "counter");
        assertGCMetric(actualMetrics,
            "jvm.gc.major_collection_time", "<OldCollectorName>", "counter");
    }
}
```

### GC with more than 2 active collectors (e.g., Generational ZGC with 4)
Use the `assertGCMetric(actualMetrics, metric, List<String> gcGenerations)` overload:
```java
assertGCMetric(actualMetrics, "jvm.gc.major_collection_count",
    Arrays.asList("<Collector1>", "<Collector2>"));
```

### Metric count formula
`hasSize(9 + N*2)` where N = number of distinct GC collector names active with that JVM flag.
- 9 = base JVM metrics (heap, threads, classes, etc.) emitted when `collect_default_jvm_metrics: true` + `new_gc_metrics: true`
- Each collector emits 2 metrics: count + time
- Examples: 2 collectors → `hasSize(13)`, 4 collectors → `hasSize(17)`

### JDK image guidance
| GC flag | JDK image |
|---|---|
| `-XX:+UseSerialGC`, `-XX:+UseParallelGC`, `-XX:+UseConcMarkSweepGC`, `-XX:+UseG1GC` | `JDK_11` |
| `-XX:+UseZGC` (non-generational) | `JDK_17` |
| `-XX:+UseZGC -XX:+ZGenerational` | `JDK_21` |
| `-XX:+UseShenandoahGC` | `JDK_17` or `JDK_21` |
| `-Xgcpolicy:gencon`, `-Xgcpolicy:balanced` | `JDK_11_OPENJ9` |
| GraalVM Native | no `MisbehavingJMXServer` support yet — note this limitation |

### Metric alias mapping
Look at the alias values in the YAML diff to determine which metric names to assert:
- `jvm.gc.minor_collection_count` / `jvm.gc.minor_collection_time` — young gen collectors
- `jvm.gc.major_collection_count` / `jvm.gc.major_collection_time` — old gen collectors
- Custom aliases (e.g., `jvm.gc.zgc_cycles_collection_count`) — assert those directly

## Code Style Requirements
- 4-space indentation, no tabs
- Lines ≤ 100 characters — wrap `appendJavaOpts(...)` onto a second line if needed
- Method name in camelCase: `testDefaultNewGCMetrics<Descriptive Suffix>`
- No Javadoc required on test methods
- Imports: add any missing ones in the correct group (static → special → third-party → java → javax)

## After Writing the Test
Run Checkstyle to verify formatting:
```bash
./mvnw checkstyle:check 2>&1 | grep -E "ERROR|WARNING|BUILD" | tail -20
```

If the new GC requires a JDK image not in `JDKImage.java`, note that it needs to be added there first.
