package org.datadog.jmxfetch.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import javax.management.MBeanServerConnection;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MetricsAssert {

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
