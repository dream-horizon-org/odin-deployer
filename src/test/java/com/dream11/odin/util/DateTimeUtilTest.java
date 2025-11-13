package com.dream11.odin.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Timestamp;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DateTimeUtilTest {
  @Test
  void testGetTimestampFromDateTime() {
    LocalDateTime localDateTime =
        LocalDateTime.of(2024, Month.FEBRUARY, 1, 12, 34, 56, 123_000_000);
    Timestamp timestamp = DateTimeUtil.getTimestampFromDateTime(localDateTime);

    assertThat(timestamp.getSeconds()).isEqualTo(localDateTime.toEpochSecond(ZoneOffset.UTC));
    assertThat(timestamp.getNanos()).isEqualTo(localDateTime.getNano());
  }
}
