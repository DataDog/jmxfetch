package org.datadog.jmxfetch;

import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class TestContainerSanity {
    @Rule
    public GenericContainer<?> cont = new GenericContainer<>("testcontainers/helloworld")
        .withExposedPorts(8080)
        .waitingFor(Wait.forHttp("/ping").forPort(8080).forStatusCode(200));

    
    @Test
    public void testSimple() throws Exception {
        assertTrue(true);
    }

}
