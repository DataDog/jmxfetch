package org.datadog.jmxfetch.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ServiceCheckHelperTest {

  @Test
  public void testFormatServiceCheckPrefix() {
    // Let's get a list of Strings to test (add real versionned check names
    // here when you add  new versionned check)
    String[][] data = {
      {"activemq_58.foo.bar12", "activemq.foo.bar12"},
      {"test_package-X86_64-VER1:0.weird.metric_name", "testpackage.weird.metric_name"}
    };

    // Let's test them all
    for (String[] datum : data) {
      assertEquals(datum[1], ServiceCheckHelper.formatServiceCheckPrefix(datum[0]));
    }
  }
}
