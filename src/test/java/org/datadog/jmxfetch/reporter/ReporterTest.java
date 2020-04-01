package org.datadog.jmxfetch.reporter;

import org.datadog.jmxfetch.Status;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ReporterTest {

    @Test
    public void statusToInteger() {
        JsonReporter jsonReporter = new JsonReporter();

        assertEquals(0, jsonReporter.statusToServiceCheckStatusValue(Status.STATUS_OK));
        assertEquals(1, jsonReporter.statusToServiceCheckStatusValue(Status.STATUS_WARNING));
        assertEquals(2, jsonReporter.statusToServiceCheckStatusValue(Status.STATUS_ERROR));
        assertEquals(3, jsonReporter.statusToServiceCheckStatusValue("XX_UNKNOWN__XX"));
    }
}
