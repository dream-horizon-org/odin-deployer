package com.dream11.odin.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApplicationUtilTest {

  @Test
  void testGenerateIntegerUUIDInRange() {
    // Act
    int generatedInteger = ApplicationUtil.generateIntegerUUID();

    // Assert
    assertThat(generatedInteger).isGreaterThanOrEqualTo(1000).isLessThanOrEqualTo(99999);
  }

  @Test
  void testGenerateIntegerUUIDRandomness() {
    // Act
    int generatedInteger1 = ApplicationUtil.generateIntegerUUID();
    int generatedInteger2 = ApplicationUtil.generateIntegerUUID();

    // Assert
    assertThat(generatedInteger1).isNotEqualTo(generatedInteger2);
  }
}
