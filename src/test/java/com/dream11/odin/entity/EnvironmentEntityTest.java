package com.dream11.odin.entity;

import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.EnvironmentStatus;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.util.EnvironmentUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EnvironmentEntityTest {
  @Test
  void testRunningStatus() {
    // Arrange
    // Act
    String status = EnvironmentUtil.getStatus(Action.CREATE_ENVIRONMENT, TaskStatus.SUCCESSFUL);
    // Assert
    Assertions.assertEquals(EnvironmentStatus.RUNNING.name(), status);
  }

  @Test
  void testDeletedStatus() {
    // Arrange
    // Act
    String status = EnvironmentUtil.getStatus(Action.DELETE_ENVIRONMENT, TaskStatus.SUCCESSFUL);
    // Assert
    Assertions.assertEquals("DELETED", status);
  }

  @Test
  void testGenericStatus() {
    // Arrange
    // Act
    String status = EnvironmentUtil.getStatus(Action.CREATE_ENVIRONMENT, TaskStatus.IN_PROGRESS);
    // Assert
    Assertions.assertEquals("CREATE_ENVIRONMENT_IN_PROGRESS", status);
  }
}
