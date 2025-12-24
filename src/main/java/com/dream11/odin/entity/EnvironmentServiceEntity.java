package com.dream11.odin.entity;

import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.TaskStatus;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class EnvironmentServiceEntity {
  long environmentId; // TODO change this to id
  String serviceName;
  Action serviceAction;
  TaskStatus serviceStatus;
  JsonObject serviceConfig;
  String createdBy;
  String updatedBy;
  LocalDateTime createdAt;
  LocalDateTime updatedAt;

  public EnvironmentServiceEntity updateStatus(TaskStatus status) {
    this.serviceStatus = status;
    return this;
  }

  public EnvironmentServiceEntity updateAction(Action action) {
    this.serviceAction = action;
    return this;
  }
}
