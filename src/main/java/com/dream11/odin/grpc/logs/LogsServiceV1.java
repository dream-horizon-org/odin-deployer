package com.dream11.odin.grpc.logs;

import com.dream11.grpc.annotation.GrpcService;
import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.service.LogsBusiness;
import com.google.inject.Inject;
import io.reactivex.Flowable;
import io.reactivex.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@GrpcService
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class LogsServiceV1 extends RxLogsServiceGrpc.LogsServiceImplBase {

  final LogsBusiness logsBusiness;

  @Override
  public Flowable<GetLogsResponse> getLogs(Single<GetLogsRequest> request) {
    return request
        .flatMapPublisher(logsBusiness::getLogs)
        .onErrorResumeNext(
            err -> {
              log.error("Error while getting logs", err);
              return Flowable.error(ExceptionUtil.parseThrowable(err));
            });
  }
}
