package com.dream11.odin.entity;

import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.TaskStatus;
import io.vertx.core.json.JsonObject;
import lombok.Builder;

@Builder
public record EnvironmentAccount(
    long id,
    long environmentId,
    TaskStatus status,
    Action action,
    String createdBy,
    JsonObject accountData,
    String accountName) {

  public EnvironmentAccount withId(long id) {
    return new EnvironmentAccount(
        id, environmentId, status, action, createdBy, accountData, accountName);
  }
}
