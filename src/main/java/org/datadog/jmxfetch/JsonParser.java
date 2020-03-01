package org.datadog.jmxfetch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unchecked")
class JsonParser {

    private Map<String, Object> parsedJson;

    public JsonParser(InputStream jsonInputStream) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        InputStreamReader jsonInputStreamReader = new InputStreamReader(jsonInputStream);
        parsedJson = mapper.readValue(jsonInputStreamReader, new TypeReference<Map>() {});
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
