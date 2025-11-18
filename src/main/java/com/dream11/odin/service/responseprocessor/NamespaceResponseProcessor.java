package com.dream11.odin.service.responseprocessor;

import com.dream11.odin.dao.EnvironmentDao;
import com.dream11.odin.dao.LockDao;
import com.dream11.odin.dto.response.ResponseMessage;
import com.google.inject.Inject;
import io.reactivex.Completable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class NamespaceResponseProcessor implements ResponseProcessor {

  final EnvironmentDao environmentDao;
  final LockDao lockDao;

  @Override
  public Completable process(ResponseMessage message) {
    return environmentDao
        .updateExecutionStatus(message)
        .andThen(environmentDao.updateEnvironmentAccountStatus(message))
        .andThen(environmentDao.setEnvironmentInActiveForDeleteEnvironmentTask(message.getId()))
        .andThen(lockDao.releaseEnvironmentExclusiveLock(message.getId()))
        .andThen(environmentDao.getEnvironmentAccount(message.getId()))
        .doOnError(err -> log.error("Error {}", err.getMessage(), err))
        .ignoreElement();
  }
}
