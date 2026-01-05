package com.dream11.odin.grpc.service;

import com.dream11.grpc.annotation.GrpcService;
import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.ApplicationContext;
import com.dream11.odin.service.ServiceBusiness;
import com.google.inject.Inject;
import io.reactivex.Flowable;
import io.reactivex.Single;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@GrpcService
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ServiceServiceV1 extends RxServiceServiceGrpc.ServiceServiceImplBase {

  final ServiceBusiness serviceBusiness;

  @Override
  public Flowable<DeployServiceResponse> deployService(Single<DeployServiceRequest> request) {

    return request
        .doOnSuccess(req -> log.info("Received deploy service request: {}", req))
        .flatMapPublisher(
            req ->
                this.serviceBusiness.deployService(
                    req,
                    ApplicationContext.getUserDetails(),
                    ApplicationContext.getTraceId())) // todo: use execution id instead
        .onErrorResumeNext(
            err -> {
              log.error("Error while deploying service", err);
              return Flowable.error(ExceptionUtil.parseThrowable(err));
            });
  }

  @Override
  @SneakyThrows
  public Flowable<OperateServiceResponse> operateService(Single<OperateServiceRequest> request) {
    return request
        .doOnSuccess(req -> log.info("Received operate service request: {}", req))
        .flatMapPublisher(
            req -> serviceBusiness.operateService(req, ApplicationContext.getUserDetails(), true))
        .onErrorResumeNext(
            err -> {
              log.error("Error while operating service", err);
              return Flowable.error(ExceptionUtil.parseThrowable(err));
            });
  }

  @Override
  public Flowable<UndeployServiceResponse> undeployService(Single<UndeployServiceRequest> request) {
    return request
        .doOnSuccess(req -> log.info("Received undeploy service request: {}", req))
        .flatMapPublisher(
            req ->
                this.serviceBusiness
                    .undeployService(
                        req.getEnvName(),
                        req.getServiceName(),
                        ApplicationContext.getUserDetails(),
                        ApplicationContext.getTraceId()) // todo: change to execution id
                    .onErrorResumeNext(
                        err -> {
                          log.error("Error while undeploying service", err);
                          return Flowable.error(ExceptionUtil.parseThrowable(err));
                        }));
  }

  @Override
  public Single<OperateComponentDiffResponse> operateComponentDiff(
      Single<OperateComponentDiffRequest> request) {
    // TODO Implement this
    return Single.just(OperateComponentDiffResponse.newBuilder().build());
    //    return request.flatMap(
    //        req ->
    //            serviceBusiness
    //                .getComponentChanges(
    //                    req.getComponentName(),
    //                    req.getServiceName(),
    //                    req.getEnvName(),
    //                    req.getOperationName(),
    //                    req.getConfig())
    //                .onErrorResumeNext(
    //                    err -> {
    //                      log.error(
    //                          "Error occurred during comparing operation : {}", err.getMessage(),
    // err);
    //                      return Single.error(ExceptionUtil.parseThrowable(err));
    //                    }));
  }
}
