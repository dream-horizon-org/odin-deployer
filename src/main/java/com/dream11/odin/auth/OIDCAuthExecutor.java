package com.dream11.odin.auth;

import static com.dream11.odin.constant.Constants.*;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.client.WebClient;
import com.dream11.odin.dto.AuthProviderData;
import com.dream11.odin.dto.TokenResponse;
import com.dream11.odin.dto.auth.AuthRequestData;
import com.dream11.odin.dto.auth.OIDCProviderDetails;
import com.dream11.odin.dto.auth.OIDCRequestData;
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
  public Single<String> authorise(AuthProviderData authProviderData, AuthRequestData requestData) {
    log.info("Starting OIDC authorization for orgId: {}", authProviderData.getOrgId());

    Long orgId = authProviderData.getOrgId();

    if (!(requestData instanceof OIDCRequestData)) {
      throw ExceptionUtil.getException(OdinError.AUTH_CODE_NOT_FOUND, orgId);
    }

    if (!(authProviderData.getProviderDetails() instanceof OIDCProviderDetails)) {
      throw ExceptionUtil.getException(OdinError.AUTH_CLIENT_CREDENTIALS_NOT_FOUND, orgId);
    }

    OIDCProviderDetails oidcDetails = (OIDCProviderDetails) authProviderData.getProviderDetails();
    oidcDetails.validateRequiredFieldsForAuth(orgId);
    OIDCRequestData oidcRequest = (OIDCRequestData) requestData;

    return this.exchangeCodeForToken(oidcDetails, oidcRequest)
        .map(this::extractUserEmail)
        .flatMap(userEmail -> generateJwtToken(userEmail, authProviderData));
  }

  private Single<TokenResponse> exchangeCodeForToken(
      OIDCProviderDetails providerDetails, OIDCRequestData requestData) {
    log.info("Exchanging authorization code for token at: {}", providerDetails.getTokenUrl());

    MultiMap form =
        MultiMap.caseInsensitiveMultiMap()
            .add(GRANT_TYPE, AUTHORIZATION_CODE)
            .add(CODE, requestData.getAuthorizationCode())
            .add(REDIRECT_URI, requestData.getRedirectUri())
            .add(CLIENT_ID, providerDetails.getClientId())
            .add(CLIENT_SECRET, providerDetails.getClientSecret());

    return webClient
        .getWebClient()
        .postAbs(providerDetails.getTokenUrl())
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
}
