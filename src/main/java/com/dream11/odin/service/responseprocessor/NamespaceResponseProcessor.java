package com.dream11.odin.service.responseprocessor;

import com.dream11.odin.dao.EnvironmentDao;
import com.dream11.odin.dto.response.ResponseMessage;
import com.google.inject.Inject;
import io.reactivex.Completable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class NamespaceResponseProcessor implements ResponseProcessor {

  final EnvironmentDao environmentDao;

  @Override
  public Completable process(ResponseMessage message) {
    return environmentDao
        .updateEnvironmentTaskStatus(message)
        .andThen(environmentDao.setEnvironmentInActiveForDeleteEnvironmentTask(message.getId()))
        .andThen(environmentDao.getEnvironmentTask(message.getId()))
        .doOnError(err -> log.error("Error {}", err.getMessage(), err))
        .ignoreElement();
  }
}
