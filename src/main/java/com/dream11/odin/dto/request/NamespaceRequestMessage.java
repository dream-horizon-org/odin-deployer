package com.dream11.odin.dto.request;

import com.dream11.odin.constant.Action;
import com.dream11.odin.dto.constants.RequestMessageType;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class NamespaceRequestMessage {
  final String environmentName;
  final JsonObject accountData;
  final Action environmentAction;
  final long environmentAccountId;
  final Long orgId;
  final String traceId;

  public JsonObject createRequest() {
    JsonObject body =
        JsonObject.of(
            "name", environmentName,
            "action", environmentAction,
            "account", accountData,
            "orgId", orgId);
    return JsonObject.of(
        "id", environmentAccountId,
        "type", RequestMessageType.NAMESPACE,
        "body", body,
        "traceId", traceId);
  }
}
