# Dynamic Tags Feature for JMXFetch

## Overview

JMXFetch now supports **dynamic tags** - the ability to extract values from JMX bean attributes and use them as tags on all emitted metrics. This is useful for adding contextual information like cluster IDs, version numbers, broker IDs, or other dynamic configuration values that are exposed via JMX.

## Syntax

Dynamic tags use a special syntax in the `tags` configuration:

```yaml
tag_name:$bean_name#AttributeName
```

OR with the `attribute.` prefix for clarity:

```yaml
tag_name:$bean_name#attribute.AttributeName
```

### Components:
- `tag_name`: The name you want for the tag (e.g., `cluster_id`, `kafka_version`)
- `$`: Prefix indicating this is a dynamic tag reference
- `bean_name`: The full JMX ObjectName (e.g., `kafka.server:type=KafkaServer,name=ClusterId`)
- `#`: Separator between bean name and attribute
- `AttributeName`: The name of the MBean attribute to fetch (e.g., `Value`, `Version`)

## Examples

### Kafka Cluster ID

```yaml
instances:
  - host: kafka
    port: 9101
    tags:
      - env:production
      - cluster_id:$kafka.server:type=KafkaServer,name=ClusterId#Value
    conf:
      - include:
          domain: kafka.server
```

This will:
1. Connect to the Kafka JMX server
2. Fetch the value of `kafka.server:type=KafkaServer,name=ClusterId` → `Value` attribute
3. Add a tag like `cluster_id:abc-xyz-123` to **all** metrics emitted from this instance

### Multiple Dynamic Tags

```yaml
instances:
  - host: application-server
    port: 9999
    tags:
      - env:local
      - cluster_id:$kafka.server:type=KafkaServer,name=ClusterId#Value
      - broker_id:$kafka.server:type=KafkaServer,name=BrokerId#Value
      - version:$kafka.server:type=app-info,id=0#Version
```

### Map-style Tags

You can also use map-style tag configuration:

```yaml
instances:
  - host: kafka
    port: 9101
    tags:
      env: production
      service: kafka
      cluster_id: $kafka.server:type=KafkaServer,name=ClusterId#Value
```

## How It Works

1. **Parse Phase**: When the instance configuration is loaded, JMXFetch identifies dynamic tags (those starting with `$`)
2. **Connection Phase**: After establishing the JMX connection, JMXFetch resolves the dynamic tags by fetching the referenced attribute values
3. **Collection Phase**: The resolved tag values are applied to all metrics collected from that instance

## Features

- **Type Conversion**: Attribute values are automatically converted to strings (works with String, Integer, Long, Double, etc.)
- **Graceful Degradation**: If a dynamic tag cannot be resolved (e.g., bean doesn't exist), the metric collection continues without that tag
- **Logging**: Resolution success/failure is logged for debugging
- **Performance**: Dynamic tags are resolved once at connection time, not on every metric collection

## Error Handling

If a dynamic tag fails to resolve:
- A warning is logged with the reason
- Metrics are still collected and emitted
- Other dynamic tags continue to be resolved
- Only the failed tag is omitted

Example log output:
```
INFO: Resolved dynamic tag 'cluster_id' to value 'prod-cluster-01' from bean 'kafka.server:type=KafkaServer,name=ClusterId' attribute 'Value'
WARN: Failed to resolve dynamic tag 'broker_id' from bean 'kafka.server:type=NonExistent,name=BrokerId' attribute 'Value': InstanceNotFoundException
```

## Testing

The feature includes comprehensive tests covering:
- Basic dynamic tag resolution (list-style tags)
- Map-style tag configuration
- Multiple dynamic tags from different beans
- Integer/numeric attribute values
- Non-existent beans (error handling)
- Mixed static and dynamic tags

Run tests with:
```bash
mvn test -Dtest=TestDynamicTags
```

## Implementation Details

### New Classes
- `DynamicTag.java`: Handles parsing and resolution of dynamic tag references
  - `parse()`: Parses tag syntax
  - `resolve()`: Fetches attribute value from JMX
  - `resolveAll()`: Batch resolution of multiple dynamic tags

### Modified Classes
- `Instance.java`:
  - Added `dynamicTags` field to store parsed dynamic tags
  - Modified `getTagsMap()` to skip dynamic tags during initial parsing
  - Added `resolveDynamicTags()` called after JMX connection is established
  - Dynamic tags are resolved and added to the instance tags map

## Limitations

- Dynamic tags are resolved once at connection time (not per collection cycle)
- If a bean attribute value changes, you need to restart/reconnect to pick up the new value
- Currently only supports simple attribute types (String, Integer, Long, Double, Boolean)
- Complex types (CompositeData, TabularData) are not supported as tag values

## Future Enhancements

Potential improvements:
- Periodic re-resolution of dynamic tags (e.g., every N minutes)
- Support for nested attributes in CompositeData (e.g., `#Memory.used`)
- Template syntax for combining multiple attributes (e.g., `{#Attr1}-{#Attr2}`)
- Per-metric dynamic tags (in addition to instance-level)

## Migration Guide

If you're currently hardcoding values that could be dynamic:

**Before:**
```yaml
instances:
  - host: kafka-1
    port: 9101
    tags:
      - cluster_id:prod-cluster-01  # Hardcoded!
```

**After:**
```yaml
instances:
  - host: kafka-1
    port: 9101
    tags:
      - cluster_id:$kafka.server:type=KafkaServer,name=ClusterId#Value  # Dynamic!
```

## Support

The feature is backward compatible - existing configurations without dynamic tags continue to work unchanged.


