package com.dream11.odin.service.responseprocessor;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.dao.EnvironmentDao;
import com.dream11.odin.dao.LockDao;
import com.dream11.odin.dto.response.ResponseMessage;
import com.dream11.odin.error.OdinError;
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
    if (!message.getData().containsKey("accountName")) {
      return Completable.error(
          ExceptionUtil.getException(
              OdinError.INVALID_NAMESPACE_RESPONSE_DATA, "accountName not found"));
    }
    return this.environmentDao
        .updateExecutionStatus(
            message.getExecutionId(),
            message.getData().get("accountName").toString(),
            message.getStatus(),
            message.toString())
        .andThen(
            this.environmentDao.updateEnvironmentAccountStatus(
                message.getId(), message.getStatus()))
        .andThen(
            this.environmentDao.setEnvironmentInActiveForDeleteEnvironment(
                message.getId())) // TODO why is this needed
        .andThen(this.lockDao.releaseEnvironmentExclusiveLock(message.getId()));
  }
}
