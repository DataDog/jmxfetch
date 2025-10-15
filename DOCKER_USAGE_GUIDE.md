# Using Custom JMXFetch with Docker Agent

This guide shows how to use your custom JMXFetch build (with dynamic tags feature) in a Docker-based Datadog Agent **without rebuilding anything**.

## Quick Start

### Method 1: Volume Mount (Recommended)

This is the easiest approach - just mount the custom JAR into your container.

**1. Your custom JAR is here:**
```bash
/Users/piotr.wolski/go/src/github.com/DataDog/jmxfetch/target/jmxfetch-0.50.1-SNAPSHOT-jar-with-dependencies.jar
```

**2. Add a volume mount to your docker-compose.yaml or docker run command:**

#### Docker Compose:
```yaml
version: '3'
services:
  datadog:
    image: gcr.io/datadoghq/agent:latest
    environment:
      - DD_API_KEY=your_api_key
      - DD_SITE=datadoghq.com
      # ... other env vars
    volumes:
      # Mount custom JMXFetch
      - /Users/piotr.wolski/go/src/github.com/DataDog/jmxfetch/target/jmxfetch-0.50.1-SNAPSHOT-jar-with-dependencies.jar:/opt/datadog-agent/bin/agent/dist/jmx/jmxfetch.jar:ro
      
      # Your other volumes
      - /var/run/docker.sock:/var/run/docker.sock:ro
      - ./conf.d:/etc/datadog-agent/conf.d:ro
      # ... other volumes
```

#### Docker Run:
```bash
docker run -d \
  --name datadog-agent \
  -e DD_API_KEY=your_api_key \
  -e DD_SITE=datadoghq.com \
  -v /Users/piotr.wolski/go/src/github.com/DataDog/jmxfetch/target/jmxfetch-0.50.1-SNAPSHOT-jar-with-dependencies.jar:/opt/datadog-agent/bin/agent/dist/jmx/jmxfetch.jar:ro \
  -v /var/run/docker.sock:/var/run/docker.sock:ro \
  -v /opt/datadog-agent/run:/opt/datadog-agent/run:rw \
  gcr.io/datadoghq/agent:latest
```

**3. Restart your agent:**
```bash
docker-compose restart datadog
# or
docker restart datadog-agent
```

**4. Verify it's working:**
```bash
# Check agent logs
docker logs datadog-agent | grep -i jmx

# You should see logs about dynamic tags being resolved
docker logs datadog-agent | grep -i "Resolved dynamic tag"
```

---

### Method 2: Copy into Running Container

If you already have a running container and don't want to restart with new volumes:

```bash
# Copy the JAR into the running container
docker cp \
  /Users/piotr.wolski/go/src/github.com/DataDog/jmxfetch/target/jmxfetch-0.50.1-SNAPSHOT-jar-with-dependencies.jar \
  datadog-agent:/opt/datadog-agent/bin/agent/dist/jmx/jmxfetch.jar

# Restart the agent process inside the container
docker exec datadog-agent agent restart
```

---

## Example Configuration

Now you can use dynamic tags in your JMX configuration. Create or update your Kafka integration config:

**File: `conf.d/kafka.yaml`** (or wherever your JMX configs are)

```yaml
init_config:
  is_jmx: true

instances:
  - host: kafka-broker
    port: 9999
    tags:
      - env:production
      - service:kafka
      # 🎉 NEW: Dynamic tags that pull values from JMX beans!
      - cluster_id:$kafka.server:type=KafkaServer,name=ClusterId#Value
    
    conf:
      - include:
          domain: kafka.server
          bean: kafka.server:type=BrokerTopicMetrics,name=MessagesInPerSec
          attribute:
            Count:
              metric_type: rate
              alias: kafka.messages_in
```

---

## Verification

**1. Check JMXFetch version in logs:**
```bash
docker logs datadog-agent 2>&1 | grep -i "jmxfetch version"
```

**2. Check for dynamic tag resolution:**
```bash
docker logs datadog-agent 2>&1 | grep -i "Resolving.*dynamic tag"
```

You should see logs like:
```
INFO: Resolving 1 dynamic tag(s) for instance kafka_instance
INFO: Resolved dynamic tag 'cluster_id' to value 'prod-cluster-123' from bean 'kafka.server:type=KafkaServer,name=ClusterId' attribute 'Value'
```

**3. Verify metrics have the dynamic tags:**
```bash
# In Datadog UI, check that your Kafka metrics now have the cluster_id tag
# with the actual value from your Kafka cluster
```

---

## Troubleshooting

### JAR not found error
**Error:** `FileNotFoundException: jmxfetch.jar`

**Solution:** The default JMXFetch location varies by agent version. Try these paths:
- `/opt/datadog-agent/bin/agent/dist/jmx/jmxfetch.jar` (newer versions)
- `/opt/datadog-agent/embedded/bin/jmxfetch.jar` (older versions)

Find the correct path:
```bash
docker exec datadog-agent find /opt/datadog-agent -name "jmxfetch.jar"
```

### Dynamic tags not resolving
**Check:**
1. JMX connection is successful: `docker logs datadog-agent | grep "Connected to JMX"`
2. Bean name is correct: `docker exec datadog-agent agent jmx list matching`
3. Attribute name matches exactly (case-sensitive!)

### Agent won't start
**Error:** Permission denied

**Solution:** Make sure the JAR file has read permissions:
```bash
chmod 644 /Users/piotr.wolski/go/src/github.com/DataDog/jmxfetch/target/jmxfetch-0.50.1-SNAPSHOT-jar-with-dependencies.jar
```

---

## Rolling Back

To go back to the standard JMXFetch:

**If using volume mount:**
1. Remove the volume mount from docker-compose.yaml
2. Restart: `docker-compose restart datadog`

**If you copied the file:**
```bash
# The agent will automatically download the standard version on restart
docker restart datadog-agent
```

---

## Development Workflow

When you make changes to JMXFetch:

```bash
# 1. Rebuild the JAR
cd /Users/piotr.wolski/go/src/github.com/DataDog/jmxfetch
mvn clean package -DskipTests

# 2. If using volume mount: just restart
docker-compose restart datadog

# 3. If using copy method: copy and restart
docker cp target/jmxfetch-0.50.1-SNAPSHOT-jar-with-dependencies.jar \
  datadog-agent:/opt/datadog-agent/bin/agent/dist/jmx/jmxfetch.jar
docker exec datadog-agent agent restart
```

---

## Notes

- ✅ **No rebuild needed** - Agent uses the JAR you provide
- ✅ **Hot reload** - Just restart the agent container
- ✅ **Preserves config** - All your existing configs work unchanged
- ⚠️ **Version mismatch** - If the agent version is very different from JMXFetch, you might see compatibility issues (rare)
- 💡 **Tip:** Use `:ro` (read-only) mount to prevent accidental modifications


