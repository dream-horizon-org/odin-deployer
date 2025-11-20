package com.dream11.odin.service;

import com.dream11.odin.auth.AuthExecutorFactory;
import com.dream11.odin.dao.AuthProviderDao;
import com.dream11.odin.dto.auth.AuthRequestData;
import com.dream11.odin.dto.auth.OIDCProviderDetails;
import com.dream11.odin.dto.auth.ProviderDetails;
import com.dream11.odin.grpc.auth.GetAuthProviderResponse;
import com.dream11.odin.grpc.auth.GetUserTokenResponse;
import com.dream11.odin.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.protobuf.Struct;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AuthBusiness {
  final AuthProviderDao authProviderDao;
  final AuthExecutorFactory authExecutorFactory;
  final ObjectMapper objectMapper;

  public Single<GetUserTokenResponse> getUserToken(Long orgId, Struct data) {

    /*
    fetch auth provider details from orgId: client id, client secret, token endpoint etc
    hit the token endpoint to exchange code for token
    read the token (skipping token verification for now) to get the user email (standard claim in tokens issued by openid connect
    compliant IdP's )
    once all this is done, issue your own token with the userId=email, and orgId, and any attributes if they exist
     */

    return authProviderDao
        .getAuthProviderData(orgId)
        .flatMap(
            authProviderData -> {
              JsonObject jsonObject = JsonUtil.convertProtoToJson(data);
              if (jsonObject == null) {
                jsonObject = new JsonObject();
              }

              JsonObject jsonWithType = jsonObject.copy();
              jsonWithType.put(
                  "providerType",
                  authProviderData.getType() != null
                      ? authProviderData.getType().toLowerCase()
                      : "anonymous");

              try {
                AuthRequestData requestData =
                    objectMapper.readValue(jsonWithType.encode(), AuthRequestData.class);
                requestData.validate(authProviderData.getOrgId());

                return authExecutorFactory
                    .getAuthExecutor(authProviderData.getType())
                    .authorise(authProviderData, requestData)
                    .map(token -> GetUserTokenResponse.newBuilder().setToken(token).build());
              } catch (JsonProcessingException e) {
                return Single.error(
                    new RuntimeException(
                        "Failed to deserialize AuthRequestData for provider: "
                            + authProviderData.getType(),
                        e));
              }
            });
  }

  public Single<GetAuthProviderResponse> getAuthProvider(Long orgId) {
    return authProviderDao
        .getAuthProviderData(orgId)
        .map(
            authProviderData -> {
              ProviderDetails providerDetails = authProviderData.getProviderDetails();
              // Exclude secret when sending to UI
              JsonObject providerDetailsJson =
                  providerDetails instanceof OIDCProviderDetails
                      ? ((OIDCProviderDetails) providerDetails).toJsonObject(false)
                      : providerDetails.toJsonObject();

              return GetAuthProviderResponse.newBuilder()
                  .setType(authProviderData.getType())
                  .setData(
                      JsonUtil.jsonToProtoBuilder(providerDetailsJson, Struct.newBuilder()).build())
                  .build();
            });
  }
}
