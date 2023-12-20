package org.datadog.jmxfetch.util;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.management.MBeanServerConnection;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MetricsAssert {

    public static void assertMetric(
        String name,
        Number value,
        Number lowerBound,
        Number upperBound,
        List<String> commonTags,
        List<String> additionalTags,
        int countTags,
        String metricType,
        List<Map<String, Object>> actualMetrics) {
        List<String> tags = new ArrayList<>(commonTags);
        tags.addAll(additionalTags);

        for (Map<String, Object> m : actualMetrics) {
            String mName = (String) (m.get("name"));
            Double mValue = (Double) (m.get("value"));
            Set<String> mTags = new HashSet<>(Arrays.asList((String[]) (m.get("tags"))));

            if (mName.equals(name)) {

                if (!value.equals(-1)) {
                    assertEquals((Double) value.doubleValue(), mValue);
                } else if (!lowerBound.equals(-1) || !upperBound.equals(-1)) {
                    assertTrue(mValue > (Double) lowerBound.doubleValue());
                    assertTrue(mValue < (Double) upperBound.doubleValue());
                }

                if (countTags != -1) {
                    assertEquals(countTags, mTags.size());
                }
                for (String t : tags) {
                    assertThat(mTags, hasItem(t));
                }

                if (metricType != null) {
                    assertEquals(metricType, m.get("type"));
                }
                // Brand the metric
                m.put("tested", true);

                return;
            }
        }
        fail(
            "Metric assertion failed (name: "
                + name
                + ", value: "
                + value
                + ", tags: "
                + tags
                + ", #tags: "
                + countTags
                + ").");

    }

    public static void assertDomainPresent(final String domain, final MBeanServerConnection mbs){
        assertThat(String.format("Could not find domain '%s'", domain),
            isDomainPresent(domain, mbs), equalTo(true));
    }

    public static boolean isDomainPresent(final String domain, final MBeanServerConnection mbs) {
        boolean found = false;
        try {
            final String[] domains = mbs.getDomains();
            for (String s : domains) {
                if(s.equals(domain)) {
                    found = true;
                    break;
                }
            }
        } catch (IOException e) {
            log.warn("Got an exception checking if domain is present", e);
        }
        return found;
    }
}
