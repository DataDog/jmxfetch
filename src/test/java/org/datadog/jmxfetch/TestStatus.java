package org.datadog.jmxfetch;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import org.datadog.jmxfetch.util.InstanceTelemetryMBean;
import org.junit.Test;

public class TestStatus extends TestCommon {
    
    @Test
    public void testStatusInstanceTelemetry() throws Exception {
        registerMBean(new SimpleTestJavaApp(), "org.datadog.jmxfetch.test:type=SimpleTestJavaApp");
        initApplication("jmx_status_check_instance_telemetry.yaml");
        List<Instance> instances = getInstances();
        assertEquals(1, instances.size());
        Instance inst = instances.get(0);
        List<Metric> metrics = inst.getMetrics();
        InstanceTelemetryMBean instanceTelemetryBean = inst.getInstanceTelemetryBean();
        assertEquals(17, instanceTelemetryBean.getBeansFetched());
        assertEquals(10, instanceTelemetryBean.getTopLevelAttributeCount());
        assertEquals(16, instanceTelemetryBean.getMetricCount());
    }
}
