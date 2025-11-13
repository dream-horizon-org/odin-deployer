package com.dream11.odin.service;

import com.dream11.odin.auth.AuthExecutorFactory;
import com.dream11.odin.dao.AuthProviderDao;
import com.dream11.odin.grpc.auth.GetAuthProviderResponse;
import com.dream11.odin.grpc.auth.GetUserTokenResponse;
import com.dream11.odin.util.JsonUtil;
import com.google.inject.Inject;
import com.google.protobuf.Struct;
import io.reactivex.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AuthBusiness {
  final AuthProviderDao authProviderDao;
  final AuthExecutorFactory authExecutorFactory;

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
            authProviderData ->
                authExecutorFactory
                    .getAuthExecutor(authProviderData.getType())
                    .authorise(authProviderData, JsonUtil.convertProtoToJson(data))
                    .map(token -> GetUserTokenResponse.newBuilder().setToken(token).build()));
  }

  public Single<GetAuthProviderResponse> getAuthProvider(Long orgId) {
    return authProviderDao
        .getAuthProviderDataForClient(orgId)
        .map(
            authProviderData ->
                GetAuthProviderResponse.newBuilder()
                    .setType(authProviderData.getType())
                    .setData(
                        JsonUtil.jsonToProtoBuilder(
                                authProviderData.getProviderDetails(), Struct.newBuilder())
                            .build())
                    .build());
  }
}
