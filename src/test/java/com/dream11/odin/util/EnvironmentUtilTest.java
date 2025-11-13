package com.dream11.odin.util;

import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.EnvironmentStatus;
import com.dream11.odin.constant.TaskStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EnvironmentUtilTest {

  @Test
  void testStatusRunning() {
    String status = EnvironmentUtil.getStatus(Action.CREATE_ENVIRONMENT, TaskStatus.SUCCESSFUL);
    Assertions.assertEquals(EnvironmentStatus.RUNNING.name(), status);
  }

  @Test
  void testStatusDeleted() {
    String status = EnvironmentUtil.getStatus(Action.DELETE_ENVIRONMENT, TaskStatus.SUCCESSFUL);
    Assertions.assertEquals("DELETED", status);
  }
}
