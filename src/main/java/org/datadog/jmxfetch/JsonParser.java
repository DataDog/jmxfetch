package org.datadog.jmxfetch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.jr.ob.JSON;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unchecked")
class JsonParser {

    /**
     * Hide the jackson exception inside the wrapper to avoid a dependency on jackson outside of
     * this class to distinguish between IO and JSON errors. It's needed for Java Tracer, which
     * embeds JMXFetch, excluding jackson transitive dependencies that are not needed or exercised
     * for Java Tracer use cases.
     */
    public static final class JsonException extends IOException {
        private final Exception exception;

        public JsonException(Exception exception) {
            super();
            this.exception = exception;
        }

        @Override
        public String toString() {
            return exception.toString();
        }
    }

    private Map<String, Object> parsedJson;

    public JsonParser(InputStream jsonInputStream) throws IOException {
        try {
            parsedJson = JSON.std.mapFrom(new InputStreamReader(jsonInputStream));
        } catch (JsonProcessingException ex) {
            throw new JsonException(ex);
        }
    }

    public JsonParser(JsonParser other) {
        parsedJson = new HashMap<String, Object>((Map<String, Object>) other.getParsedJson());
    }

    public Object getJsonConfigs() {
        return parsedJson.get("configs");
    }

    public Object getJsonTimestamp() {
        return parsedJson.get("timestamp");
    }

    public Object getJsonInstances(String key) {
        Map<String, Object> config =
                (Map<String, Object>)
                        ((Map<String, Object>) parsedJson.get("configs")).get(key);

        return config.get("instances");
    }

    public Object getInitConfig(String key) {
        Map<String, Object> config =
                (Map<String, Object>)
                        ((Map<String, Object>) parsedJson.get("configs")).get(key);

        return config.get("init_config");
    }

    public Object getParsedJson() {
        return parsedJson;
    }
}
