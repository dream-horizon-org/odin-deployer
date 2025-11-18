package com.dream11.odin.entity;

import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.TaskStatus;
import io.vertx.core.json.JsonObject;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class EnvironmentServiceEntity {
  String environmentName;
  String serviceName;
  Action serviceAction;
  TaskStatus serviceStatus;
  JsonObject serviceConfig;
  String createdBy;
  String updatedBy;
}
