package com.dream11.odin.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OIDCProviderConfig {
  private String tokenUrl;
  private String clientId;
  private String clientSecret;
  private String redirectUri;
  private String authorizationCode;
}
