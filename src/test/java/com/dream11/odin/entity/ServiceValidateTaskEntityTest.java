package com.dream11.odin.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ServiceValidateTaskEntityTest {
  @Test
  void createServiceValidateTaskEntityWithId() {
    // Arrange
    ServiceValidateTaskEntity serviceValidateTaskEntity =
        ServiceValidateTaskEntity.builder().name("test").build();

    // Act
    ServiceValidateTaskEntity updatedServiceValidateTaskEntity =
        serviceValidateTaskEntity.withId(1L);

    // Assert
    Assertions.assertEquals(1L, updatedServiceValidateTaskEntity.getId());
    Assertions.assertEquals("test", updatedServiceValidateTaskEntity.getName());
  }
}
