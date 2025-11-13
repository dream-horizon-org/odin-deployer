package com.dream11.odin.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ComponentValidateTaskEntityTest {

  @Test
  void createComponentValidateTaskEntityWithId() {
    // Arrange
    ComponentValidateTaskEntity componentValidateTaskEntity =
        ComponentValidateTaskEntity.builder().componentName("test").build();

    // Act
    ComponentValidateTaskEntity updatedComponentValidateTaskEntity =
        componentValidateTaskEntity.withId(1L);

    // Assert
    Assertions.assertEquals(1L, updatedComponentValidateTaskEntity.getId());
    Assertions.assertEquals("test", updatedComponentValidateTaskEntity.getComponentName());
  }
}
