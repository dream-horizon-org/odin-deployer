package com.dream11.odin.dto;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthProviderData {
  private Long orgId;
  private String type;
  private JsonObject providerDetails;
}
