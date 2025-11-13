package com.dream11.odin.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AuthConfig {
  @Valid @NotNull JwtConfig jwtConfig;
}
