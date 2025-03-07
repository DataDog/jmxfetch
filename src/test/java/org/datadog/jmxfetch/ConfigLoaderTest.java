package org.datadog.jmxfetch;

import static org.mockito.Mockito.*;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.Assert.*;

public class ConfigLoaderTest {

    @Test
    public void testLoadResourceConfigs_PrematurelyClosedStream() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        AppConfig config = mock(AppConfig.class);
        doReturn(1).when(config).getThreadPoolSize();
        doReturn(1).when(config).getReconnectionThreadPoolSize();
        doReturn(false).when(config).getJmxfetchTelemetry(); // Ensure correct return type

        List<String> resources = new ArrayList<>();
        resources.add("test.yaml");
        doReturn(resources).when(config).getInstanceConfigResources();

        Map<String, YamlParser> configs = new HashMap<>();
        App app = spy(new App(config));

        InputStream mockInputStream = mock(InputStream.class);
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        doReturn(mockInputStream).when(app).getResourceStream("test.yaml", classLoader);

        try {
            doThrow(new IOException("Stream closed")).when(mockInputStream).read(any(byte[].class));
        } catch (IOException e) {
            // Catch block for handling mock setup exceptions
        }

        Method method = App.class.getDeclaredMethod("loadResourceConfigs", AppConfig.class, Map.class);
        method.setAccessible(true);
        method.invoke(app, config, configs);

        assertTrue("Expected configs to be empty if stream is prematurely closed", configs.isEmpty());
    }

}