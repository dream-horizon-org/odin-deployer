package com.dream11.odin.util;

import com.google.protobuf.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DateTimeUtil {
  public Timestamp getTimestampFromDateTime(LocalDateTime dateTime) {
    return Timestamp.newBuilder()
        .setSeconds(dateTime.toEpochSecond(ZoneOffset.UTC))
        .setNanos(dateTime.getNano())
        .build();
  }
}
