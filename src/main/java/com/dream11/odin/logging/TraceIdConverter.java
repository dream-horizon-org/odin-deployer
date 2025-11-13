package com.dream11.odin.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.dream11.odin.ApplicationContext;

public class TraceIdConverter extends ClassicConverter {
  @Override
  public String convert(ILoggingEvent event) {
    return ApplicationContext.getTraceId();
  }
}
