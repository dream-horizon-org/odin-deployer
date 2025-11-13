package com.dream11.odin.grpc.health;

import com.dream11.grpc.annotation.GrpcService;
import com.dream11.odin.dao.HealthCheckDao;
import com.dream11.odin.grpc.health.v1.HealthCheckRequest;
import com.dream11.odin.grpc.health.v1.HealthCheckResponse;
import com.dream11.odin.grpc.health.v1.RxHealthGrpc;
import com.google.inject.Inject;
import io.reactivex.Flowable;
import io.reactivex.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@GrpcService
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class HealthServiceV1 extends RxHealthGrpc.HealthImplBase {

  private final HealthCheckDao healthCheckDao;

  @Override
  public Single<HealthCheckResponse> check(Single<HealthCheckRequest> request) {
    return checkMySqlHealthCheck(request);
  }

  @Override
  public Flowable<HealthCheckResponse> watch(Single<HealthCheckRequest> request) {
    return checkMySqlHealthCheck(request).toFlowable();
  }

  private Single<HealthCheckResponse> checkMySqlHealthCheck(Single<HealthCheckRequest> request) {

    HealthCheckResponse.Builder builder = HealthCheckResponse.newBuilder();
    return request
        .flatMap(
            req -> {
              if (req.getService().equals("liveness")) {
                return Single.just(
                    builder.setStatus(HealthCheckResponse.ServingStatus.SERVING).build());
              } else {
                return this.healthCheckDao
                    .mysqlHealthCheck()
                    .doOnSuccess(
                        status -> builder.setStatus(HealthCheckResponse.ServingStatus.SERVING));
              }
            })
        .map(status -> builder.build())
        .doOnError(err -> log.error("Error {}", err.getMessage(), err));
  }
}
