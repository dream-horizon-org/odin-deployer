package com.dream11.odin.auth;

import com.dream11.odin.dto.AuthProviderData;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;

public interface AuthExecutor {
  // Todo - convert JsonObject to proper POJO to be sent through interface and same for
  // AuthProviderData.providerDetails
  Single<String> authorise(AuthProviderData authProviderData, JsonObject requestData);
}
