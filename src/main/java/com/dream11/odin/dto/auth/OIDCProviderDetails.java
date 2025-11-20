package com.dream11.odin.dto.auth;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.error.OdinError;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.json.JsonObject;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class OIDCProviderDetails implements ProviderDetails {

  @JsonProperty("providerType")
  private String providerType; // Set by Jackson during deserialization

  @JsonProperty("token_url")
  private String tokenUrl;

  @JsonProperty("client_id")
  private String clientId;

  @JsonProperty("client_secret")
  private String clientSecret;

  @JsonProperty("authorization_url")
  private String authorizationUrl;

  @JsonProperty("scope")
  private String scope;

  @JsonProperty("name")
  private String name;

  @Override
  public JsonObject toJsonObject() {
    return toJsonObject(true);
  }

  public JsonObject toJsonObject(boolean includeSecret) {
    JsonObject json = new JsonObject();
    if (tokenUrl != null) {
      json.put("token_url", tokenUrl);
    }
    if (clientId != null) {
      json.put("client_id", clientId);
    }
    if (includeSecret && clientSecret != null) {
      json.put("client_secret", clientSecret);
    }
    if (authorizationUrl != null) {
      json.put("authorization_url", authorizationUrl);
    }
    if (scope != null) {
      json.put("scope", scope);
    }
    if (name != null) {
      json.put("name", name);
    }
    return json;
  }

  public static OIDCProviderDetails fromJsonObjectWithoutSecret(JsonObject jsonObject) {
    if (jsonObject == null) {
      return new OIDCProviderDetails();
    }

    return OIDCProviderDetails.builder()
        .tokenUrl(jsonObject.getString("token_url"))
        .clientId(jsonObject.getString("client_id"))
        .authorizationUrl(jsonObject.getString("authorization_url"))
        .scope(jsonObject.getString("scope"))
        .name(jsonObject.getString("name"))
        .build();
  }

  public void validateRequiredFieldsForAuth(Long orgId) {
    if (tokenUrl == null || tokenUrl.isBlank()) {
      throw ExceptionUtil.getException(OdinError.AUTH_CLIENT_CREDENTIALS_NOT_FOUND, orgId);
    }
    if (clientId == null || clientId.isBlank()) {
      throw ExceptionUtil.getException(OdinError.AUTH_CLIENT_CREDENTIALS_NOT_FOUND, orgId);
    }
    if (clientSecret == null || clientSecret.isBlank()) {
      throw ExceptionUtil.getException(OdinError.AUTH_CLIENT_CREDENTIALS_NOT_FOUND, orgId);
    }
  }
}
