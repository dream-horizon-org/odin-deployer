package com.dream11.odin.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.Test;

public class LogUtilTest {
  @Test
  public void testGetLogLevel() {
    String logWithAnsi = "\u001B[32mDEBUG\u001B[0m 2025-05-07 08:31:53 Some debug log";
    String logWithoutAnsi = "INFO 2025-05-07 10:00:00 Something happened";
    String logWithUnknown = "Something went wrong, no level here";

    assertEquals("DEBUG", LogUtil.getLogLevel(logWithAnsi));
    assertEquals("INFO", LogUtil.getLogLevel(logWithoutAnsi));
    assertEquals("UNKNOWN", LogUtil.getLogLevel(logWithUnknown));
  }

  @Test
  public void testAddDebugLogLevelIfNoLevelPresent() {
    // Case 1: Log with a level should remain unchanged
    String logWithLevel = "INFO Some log message";
    String resultWithLevel = LogUtil.addDebugLogLevelIfNoLevelPresent(logWithLevel);
    assertEquals(logWithLevel, resultWithLevel);

    // Case 2: Log without a level should be prefixed with ANSI-colored DEBUG
    String logWithoutLevel = "Some log message";
    String result = LogUtil.addDebugLogLevelIfNoLevelPresent(logWithoutLevel);

    // Strip ANSI to validate logically
    String cleanResult = LogUtil.stripAnsiCodes(result);
    assertTrue(cleanResult.startsWith("DEBUG"));
    assertTrue(cleanResult.endsWith("Some log message"));
  }
}
