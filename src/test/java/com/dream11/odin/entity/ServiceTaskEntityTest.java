package com.dream11.odin.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ServiceTaskEntityTest {
  @Test
  void createServiceTaskEntityWithId() {
    // Arrange
    ServiceTaskEntity serviceTaskEntity = ServiceTaskEntity.builder().name("test").build();

    // Act
    ServiceTaskEntity updatedServiceTaskEntity = serviceTaskEntity.withId(1L);

    // Assert
    Assertions.assertEquals(1L, updatedServiceTaskEntity.getId());
    Assertions.assertEquals("test", updatedServiceTaskEntity.getName());
  }
}
