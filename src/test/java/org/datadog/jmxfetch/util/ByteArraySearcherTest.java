package org.datadog.jmxfetch.util;

import static org.junit.Assert.assertEquals;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ByteArraySearcherTest {

  private final byte[] term;
  private final byte[] content;
  private final boolean matches;

  public ByteArraySearcherTest(String content, String term, boolean matches) {
    this.content = content.getBytes(Charset.forName("UTF-8"));
    this.term = term.getBytes(Charset.forName("UTF-8"));
    this.matches = matches;
  }

  @Parameterized.Parameters
  public static Iterable<Object[]> testCases() {
    return Arrays.asList(
        new Object[][] {
          {"foobar", "foo", true},
          {"foofoo", "foo", true},
          {"", "foo", false},
          {"bar", "foo", false},
          {"barbarfoo", "foo", true},
          {"barbarfoobar", "foo", true},
          {"fofofofofo", "foo", false},
          {UUID.randomUUID().toString(), "pqrst", false},
        });
  }

  @Test
  public void testFindTermInContent() {
    assertEquals(matches, new ByteArraySearcher(term).matches(content));
  }
}
