package com.dream11.odin.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogUtil {
  private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[0-9;]*m");
  private static final Pattern LOG_LEVEL_PATTERN =
      Pattern.compile("\\b(DEBUG|INFO|WARN|ERROR|TRACE)\\b");

  public static String addDebugLogLevelIfNoLevelPresent(String log) {
    if (containsLogLevel(log)) {
      return log;
    }

    final String ESC = "\u001B";
    return ESC + "[39mDEBUG" + ESC + "[0;39m " + ESC + "[32m" + log + ESC + "[0m";
  }

  public static String getLogLevel(String log) {
    String cleanLog = stripAnsiCodes(log);
    Matcher matcher = LOG_LEVEL_PATTERN.matcher(cleanLog);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return "UNKNOWN";
  }

  private static boolean containsLogLevel(String log) {
    String cleanLog = stripAnsiCodes(log);
    Matcher matcher = LOG_LEVEL_PATTERN.matcher(cleanLog);
    return matcher.find();
  }

  public static String stripAnsiCodes(String input) {
    return ANSI_PATTERN.matcher(input).replaceAll("");
  }
}
