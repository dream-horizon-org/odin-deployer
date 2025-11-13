package com.dream11.odin.auth;

import static com.dream11.odin.constant.Constants.*;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.client.WebClient;
import com.dream11.odin.dto.AuthProviderData;
import com.dream11.odin.dto.TokenResponse;
import com.dream11.odin.dto.auth.OIDCProviderConfig;
import com.dream11.odin.error.OdinError;
import com.google.inject.Inject;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.predicate.ResponsePredicate;
import io.vertx.reactivex.ext.web.codec.BodyCodec;
import java.util.Base64;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class OIDCAuthExecutor implements AuthExecutor {

  private final JwtService jwtService;
  private final WebClient webClient;

  @Override
  public Single<String> authorise(AuthProviderData authProviderData, JsonObject requestData) {
    log.info("Starting OIDC authorization for orgId: {}", authProviderData.getOrgId());

    return this.exchangeCodeForToken(buildOIDCConfig(authProviderData, requestData))
        .map(this::extractUserEmail)
        .flatMap(userEmail -> generateJwtToken(userEmail, authProviderData));
  }

  private OIDCProviderConfig buildOIDCConfig(AuthProviderData provider, JsonObject request) {
    Long orgId = provider.getOrgId();

    String code =
        requireNonEmpty(
            request.getString(AUTHORIZATION_CODE), OdinError.AUTH_CODE_NOT_FOUND, orgId);
    String redirectUri =
        requireNonEmpty(
            request.getString(REDIRECT_URI), OdinError.AUTH_REDIRECT_URL_NOT_FOUND, orgId);

    JsonObject details = provider.getProviderDetails();
    String tokenUrl =
        requireNonEmpty(
            details.getString(TOKEN_URL), OdinError.AUTH_CLIENT_CREDENTIALS_NOT_FOUND, orgId);
    String clientId =
        requireNonEmpty(
            details.getString(CLIENT_ID), OdinError.AUTH_CLIENT_CREDENTIALS_NOT_FOUND, orgId);
    String clientSecret =
        requireNonEmpty(
            details.getString(CLIENT_SECRET), OdinError.AUTH_CLIENT_CREDENTIALS_NOT_FOUND, orgId);

    return OIDCProviderConfig.builder()
        .tokenUrl(tokenUrl)
        .clientId(clientId)
        .clientSecret(clientSecret)
        .redirectUri(redirectUri)
        .authorizationCode(code)
        .build();
  }

  private Single<TokenResponse> exchangeCodeForToken(OIDCProviderConfig config) {
    log.info("Exchanging authorization code for token at: {}", config.getTokenUrl());

    MultiMap form =
        MultiMap.caseInsensitiveMultiMap()
            .add(GRANT_TYPE, AUTHORIZATION_CODE)
            .add(CODE, config.getAuthorizationCode())
            .add(REDIRECT_URI, config.getRedirectUri())
            .add(CLIENT_ID, config.getClientId())
            .add(CLIENT_SECRET, config.getClientSecret());

    return webClient
        .getWebClient()
        .postAbs(config.getTokenUrl())
        .as(BodyCodec.json(TokenResponse.class))
        .putHeader(CONTENT_TYPE, APPLICATION_FORM_TYPE)
        .expect(ResponsePredicate.SC_SUCCESS)
        .rxSendForm(form)
        .map(HttpResponse::body);
  }

  private String extractUserEmail(TokenResponse tokenResponse) {
    try {
      String idToken = tokenResponse.getIdToken();
      if (idToken == null) throw ExceptionUtil.getException(OdinError.AUTH_ID_TOKEN_NOT_FOUND);

      String[] parts = idToken.split("\\.");
      if (parts.length < 2) throw ExceptionUtil.getException(OdinError.AUTH_ID_TOKEN_INVALID);

      String payload = new String(Base64.getUrlDecoder().decode(parts[1]));

      String email = new JsonObject(payload).getString(EMAIL);
      if (email == null) throw ExceptionUtil.getException(OdinError.AUTH_EMAIL_NOT_FOUND);

      return email;

    } catch (IllegalArgumentException e) {
      throw ExceptionUtil.getException(OdinError.AUTH_ID_TOKEN_PARSE_FAILED);
    }
  }

  private Single<String> generateJwtToken(String userEmail, AuthProviderData authProviderData) {
    return jwtService.generateToken(userEmail, Map.of(ORGID, authProviderData.getOrgId()));
  }

  private String requireNonEmpty(String value, OdinError error, Long orgId) {
    if (value == null || value.isBlank()) {
      throw ExceptionUtil.getException(error, orgId);
    }
    return value;
  }
}
