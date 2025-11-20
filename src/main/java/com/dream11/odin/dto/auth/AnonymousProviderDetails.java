package com.dream11.odin.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.json.JsonObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode
public class AnonymousProviderDetails implements ProviderDetails {

  @JsonProperty("providerType")
  private String providerType;

  @Override
  public JsonObject toJsonObject() {
    return new JsonObject();
  }
}
