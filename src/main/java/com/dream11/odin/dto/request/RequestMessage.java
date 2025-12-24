package com.dream11.odin.dto.request;

import com.dream11.odin.constant.Action;
import com.dream11.odin.dto.constants.RequestMessageType;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RequestMessage {
  final String environmentName;
  final JsonObject accountData;
  final Action environmentAction;
  final long taskId;
  final RequestMessageType requestMessageType;
  final Long orgId;
  final String traceId;

  public JsonObject createRequest() {
    JsonObject body =
        new JsonObject()
            .put("name", environmentName)
            .put("action", environmentAction)
            .put("account", accountData)
            .put("orgId", orgId);
    return new JsonObject()
        .put("id", taskId)
        .put("type", requestMessageType)
        .put("body", body)
        .put("traceId", traceId);
  }
}
