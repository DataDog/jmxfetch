package org.datadog.jmxfetch;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

import com.google.gson.Gson;

@SuppressWarnings("unchecked")
class JsonParser {

    private HashMap<Object, Object> parsedJson;

    public JsonParser(InputStream jsonInputStream) {
        InputStreamReader jsonInputStreamReader = new InputStreamReader(jsonInputStream);
        parsedJson = (HashMap<Object, Object>) new Gson().fromJson(jsonInputStreamReader, Object.class);
    }

    public JsonParser(JsonParser other) {
        parsedJson = new HashMap<Object, Object>((HashMap<Object, Object>) other.getParsedJson());
    }

    public Object getJsonConfigs() {
        return parsedJson.get("configs");
    }

    public Object getJsonInstances(String key) {
        HashMap<Object, Object> config = (HashMap<Object, Object>) ((HashMap<Object, Object>) parsedJson
                .get("configs")).get(key);
        return config.get("instances");
    }

    public Object getInitConfig(String key) {
        HashMap<Object, Object> config = (HashMap<Object, Object>) ((HashMap<Object, Object>) parsedJson
                .get("configs")).get(key);
        return config.get("init_config");
    }

    public Object getParsedJson() {
        return parsedJson;
    }

}
