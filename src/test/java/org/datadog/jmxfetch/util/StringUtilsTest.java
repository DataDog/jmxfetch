package org.datadog.jmxfetch.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class StringUtilsTest {


    @Parameterized.Parameters
    public static Iterable<Object[]> testCases() {
        return Arrays.asList(new Object[][] {
                {Collections.emptyList(), ":", ""},
                {Collections.singletonList("foo"), ":", "foo"},
                {Arrays.asList("foo", "bar"), ":", "foo:bar"},
                {Arrays.asList("foo", "bar"), "::", "foo::bar"},
                {Arrays.asList("foo", "bar", "qux"), ":", "foo:bar:qux"},
                {Arrays.asList("foo", "bar", "qux"), "::", "foo::bar::qux"}
        });
    }

    private final Collection<String> parts;
    private final String delimiter;
    private final String expected;

    public StringUtilsTest(Collection<String> parts, String delimiter, String expected) {
        this.parts = parts;
        this.delimiter = delimiter;
        this.expected = expected;
    }

    @Test
    public void testJoinCollection() {
        assertEquals(expected, StringUtils.join(delimiter, parts));
    }

    @Test
    public void testJoinArray() {
        assertEquals(expected, StringUtils.join(delimiter, parts.toArray(new String[0])));
    }

}
