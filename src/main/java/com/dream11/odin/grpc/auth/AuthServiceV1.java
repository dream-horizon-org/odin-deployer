package com.dream11.odin.grpc.auth;

import com.dream11.grpc.annotation.GrpcService;
import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.service.AuthBusiness;
import com.google.inject.Inject;
import io.reactivex.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@GrpcService
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AuthServiceV1 extends RxAuthServiceGrpc.AuthServiceImplBase {
  final AuthBusiness authBusiness;

  @Override
  public Single<GetUserTokenResponse> getUserToken(Single<GetUserTokenRequest> request) {
    return request
        .flatMap(req -> authBusiness.getUserToken(req.getOrgId(), req.getData()))
        .onErrorResumeNext(err -> Single.error(ExceptionUtil.parseThrowable(err)))
        .doOnError(err -> log.error("Error {}", err.getMessage(), err));
  }

  @Override
  public Single<GetAuthProviderResponse> getAuthProvider(Single<GetAuthProviderRequest> request) {

    return request
        .flatMap(req -> authBusiness.getAuthProvider(req.getOrgId()))
        .doOnError(err -> log.error("Error {}", err.getMessage(), err))
        .onErrorResumeNext(err -> Single.error(ExceptionUtil.parseThrowable(err)));
  }
}
