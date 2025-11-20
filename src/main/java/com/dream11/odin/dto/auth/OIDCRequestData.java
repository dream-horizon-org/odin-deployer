package com.dream11.odin.dto.auth;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.error.OdinError;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OIDCRequestData implements AuthRequestData {
  @JsonProperty("providerType")
  private String providerType; // Set by Jackson during deserialization

  @JsonProperty("authorization_code")
  private String authorizationCode;

  @JsonProperty("redirect_uri")
  private String redirectUri;

  @Override
  public void validate(Long orgId) {
    if (authorizationCode == null || authorizationCode.isBlank()) {
      throw ExceptionUtil.getException(OdinError.AUTH_CODE_NOT_FOUND, orgId);
    }
    if (redirectUri == null || redirectUri.isBlank()) {
      throw ExceptionUtil.getException(OdinError.AUTH_REDIRECT_URL_NOT_FOUND, orgId);
    }
  }
}
