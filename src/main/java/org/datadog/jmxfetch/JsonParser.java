package org.datadog.jmxfetch;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.HashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

@SuppressWarnings("unchecked")
class JsonParser {

    private HashMap<String, Object> parsedJson;

    public JsonParser(InputStream jsonInputStream) throws IOException{
        ObjectMapper mapper = new ObjectMapper();
        InputStreamReader jsonInputStreamReader = new InputStreamReader(jsonInputStream);
        parsedJson = mapper.readValue(jsonInputStreamReader, new TypeReference<HashMap>(){});
    }

    public JsonParser(JsonParser other) {
        parsedJson = new HashMap<String, Object>((HashMap<String, Object>) other.getParsedJson());
    }

    public Object getJsonConfigs() {
        return parsedJson.get("configs");
    }

    public Object getJsonInstances(String key) {
        HashMap<String, Object> config = (HashMap<String, Object>) ((HashMap<String, Object>) parsedJson
                .get("configs")).get(key);
        return config.get("instances");
    }

    public Object getInitConfig(String key) {
        HashMap<String, Object> config = (HashMap<String, Object>) ((HashMap<String, Object>) parsedJson
                .get("configs")).get(key);
        return config.get("init_config");
    }

    public Object getParsedJson() {
        return parsedJson;
    }

}
