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
    // TODO update status for only target environment account
    return this.environmentDao
        .updateExecutionStatus(message)
        .andThen(
            this.environmentDao.updateEnvironmentAccountStatus(
                message.getId(), message.getStatus()))
        .andThen(
            this.environmentDao.setEnvironmentInActiveForDeleteEnvironmentTask(
                message.getId())) // TODO why is this needed
        .andThen(this.lockDao.releaseEnvironmentExclusiveLock(message.getId()));
  }
}
