package com.dream11.odin.dto.auth;

import static com.dream11.odin.constant.Constants.ANONYMOUS;
import static com.dream11.odin.constant.Constants.OIDC;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.vertx.core.json.JsonObject;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "providerType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = OIDCProviderDetails.class, name = OIDC),
  @JsonSubTypes.Type(value = AnonymousProviderDetails.class, name = ANONYMOUS)
})
public interface ProviderDetails {
  JsonObject toJsonObject();
}
