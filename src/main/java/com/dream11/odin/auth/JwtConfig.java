package com.dream11.odin.auth;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtConfig {
  @NotNull String privateKey;
  @NotNull String publicKey;
  @NotNull long expirationMillis;
}
