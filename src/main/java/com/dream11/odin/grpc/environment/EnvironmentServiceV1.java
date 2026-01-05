package com.dream11.odin.grpc.environment;

import static com.dream11.odin.constant.Constants.ACCOUNT_PARAM;
import static com.dream11.odin.constant.Constants.DISPLAY_ALL_PARAM;

import com.dream11.grpc.annotation.GrpcService;
import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.ApplicationContext;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.dto.UserDetails;
import com.dream11.odin.service.EnvironmentBusiness;
import com.google.inject.Inject;
import io.reactivex.Flowable;
import io.reactivex.Single;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@GrpcService
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class EnvironmentServiceV1 extends RxEnvironmentServiceGrpc.EnvironmentServiceImplBase {
  final EnvironmentBusiness environmentBusiness;

  @Override
  public Single<ListEnvironmentResponse> listEnvironment(Single<ListEnvironmentRequest> request) {
    final UserDetails userDetails = ApplicationContext.getUserDetails();
    return request
        .flatMap(
            req ->
                environmentBusiness.listEnvironment(
                    userDetails.getUserId(),
                    userDetails.getOrgId(),
                    Boolean.valueOf(req.getParamsMap().get(DISPLAY_ALL_PARAM)),
                    req.getParamsMap().get(ACCOUNT_PARAM)))
        .doOnError(err -> log.error("Error while listing environment", err))
        .onErrorResumeNext(err -> Single.error(ExceptionUtil.parseThrowable(err)));
  }

  @Override
  public Single<DescribeEnvironmentResponse> describeEnvironment(
      Single<DescribeEnvironmentRequest> request) {
    final UserDetails userDetails = ApplicationContext.getUserDetails();
    return request
        .flatMap(
            req ->
                environmentBusiness.describeEnvironment(
                    userDetails.getOrgId(), req.getEnvName(), req.getParamsMap()))
        .doOnError(err -> log.error("Error while describing environment", err))
        .onErrorResumeNext(err -> Single.error(ExceptionUtil.parseThrowable(err)));
  }

  @Override
  @SneakyThrows
  public Flowable<CreateEnvironmentResponse> createEnvironment(
      Single<CreateEnvironmentRequest> request) {
    final UserDetails userDetails = ApplicationContext.getUserDetails();

    return request
        .flatMapPublisher(
            req ->
                this.environmentBusiness.createEnvironment(
                    req.getEnvName(), req.getAccountsList(), userDetails))
        .onErrorResumeNext(
            err -> {
              log.error("Error while creating environment", err);
              return Flowable.error(ExceptionUtil.parseThrowable(err));
            });
  }

  @Override
  public Flowable<DeleteEnvironmentResponse> deleteEnvironment(
      Single<DeleteEnvironmentRequest> request) {
    final UserDetails userDetails = ApplicationContext.getUserDetails();
    return request
        .flatMapPublisher(
            req -> environmentBusiness.deleteEnvironment(userDetails.getOrgId(), req.getEnvName()))
        .onErrorResumeNext(
            err -> {
              log.error(String.format(Constants.ERROR_MESSAGE_FORMAT, err.getMessage()), err);
              return Flowable.error(ExceptionUtil.parseThrowable(err));
            });
  }

  @Override
  public Flowable<StatusEnvironmentResponse> statusEnvironment(
      Single<StatusEnvironmentRequest> request) {
    final UserDetails userDetails = ApplicationContext.getUserDetails();
    // TODO
    return Flowable.just(StatusEnvironmentResponse.newBuilder().build());
    //    return request
    //        .flatMapPublisher(
    //            req ->
    //                environmentBusiness.getEnvironmentStatus(
    //                    userDetails.getOrgId(), req.getEnvName(), req.getServiceName(),
    // userDetails))
    //        .onErrorResumeNext(
    //            err -> {
    //              log.error(String.format(Constants.ERROR_MESSAGE_FORMAT, err.getMessage()), err);
    //              return Flowable.error(ExceptionUtil.parseThrowable(err));
    //            });
  }
}
