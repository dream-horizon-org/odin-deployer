package com.dream11.odin.auth;

import static com.dream11.odin.constant.Constants.OIDC;

import com.dream11.odin.client.WebClient;
import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AuthExecutorFactory {
  final JwtService jwtService;
  final WebClient webClient;

  public AuthExecutor getAuthExecutor(String authProviderType) {
    switch (authProviderType.toLowerCase()) {
      case OIDC:
        return new OIDCAuthExecutor(jwtService, webClient);
      default:
        return new AnonymousAuthExecutor(jwtService);
    }
  }
}
