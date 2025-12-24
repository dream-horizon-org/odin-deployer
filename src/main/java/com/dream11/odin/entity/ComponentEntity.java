package com.dream11.odin.entity;

import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.TaskStatus;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class ComponentEntity {

  String name;
  Action action;
  TaskStatus status;
  JsonObject config;
  String createdBy;
  String updatedBy;
  LocalDateTime createdAt;
  LocalDateTime updatedAt;
  JsonObject accountData;

  public ComponentEntity updateAction(Action action) {
    this.action = action;
    return this;
  }

  public ComponentEntity updateStatus(TaskStatus status) {
    this.status = status;
    return this;
  }
}
