package com.dream11.odin.entity;

import com.dream11.odin.constant.Action;
import io.vertx.core.json.JsonObject;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class ExecutionTaskEntity {
  long id;
  Action action;
  long orgId;
  String status;
  String entity;
  String executionId;
  JsonObject response;
  JsonObject payload;
}
