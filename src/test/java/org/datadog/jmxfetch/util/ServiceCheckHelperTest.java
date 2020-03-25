package org.datadog.jmxfetch.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ServiceCheckHelperTest {

    @Test
    public void testFormatServiceCheckPrefix() {
        assertEquals("foo.my_check_name",
                ServiceCheckHelper.formatServiceCheckPrefix("foo.my_check_name"));
        assertEquals("foobar.my_check_name",
                ServiceCheckHelper.formatServiceCheckPrefix("foo_bar.my_check_name"));
        assertEquals("foobaz.my_check_name123ABC",
                ServiceCheckHelper.formatServiceCheckPrefix("foo_123bazABC.my_check_name123ABC"));
    }

}