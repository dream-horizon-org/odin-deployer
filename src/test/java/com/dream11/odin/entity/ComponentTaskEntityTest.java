package com.dream11.odin.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ComponentTaskEntityTest {

  @Test
  void createComponentTaskEntityWithId() {
    // Arrange
    ComponentTaskEntity componentTaskEntity =
        ComponentTaskEntity.builder().componentName("test").build();

    // Act
    ComponentTaskEntity updatedComponentTaskEntity = componentTaskEntity.withId(1L);

    // Assert
    Assertions.assertEquals(1L, updatedComponentTaskEntity.getId());
    Assertions.assertEquals("test", updatedComponentTaskEntity.getComponentName());
  }
}
