package com.dream11.odin.service.responseprocessor;

import com.dream11.odin.constant.EnvironmentStatus;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dao.EnvironmentDao;
import com.dream11.odin.dao.LockDao;
import com.dream11.odin.dto.response.NamespaceResponseData;
import com.dream11.odin.dto.response.ResponseMessage;
import com.dream11.odin.entity.EnvironmentAccount;
import com.dream11.odin.entity.EnvironmentEntityWithEnvironmentAccounts;
import com.google.inject.Inject;
import io.reactivex.Completable;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class NamespaceResponseProcessor implements ResponseProcessor {

  final EnvironmentDao environmentDao;
  final LockDao lockDao;

  @Override
  public Completable process(ResponseMessage message) {
    return this.environmentDao
        .updateExecutionStatus(
            message.getExecutionId(),
            ((NamespaceResponseData) message.getData()).getAccountName(),
            message.getStatus(),
            message.toString())
        .andThen(
            this.environmentDao.updateEnvironmentAccountStatus(
                message.getId(), message.getStatus()))
        .andThen(this.environmentDao.getEnvironmentIdFromEnvironmentAccountId(message.getId()))
        .flatMap(this.environmentDao::getEnvironmentById)
        .flatMapCompletable(
            envWithAccounts ->
                this.handleEnvironmentDeletion(envWithAccounts)
                    .andThen(this.handleLockRelease(envWithAccounts)));
  }

  private Completable handleLockRelease(EnvironmentEntityWithEnvironmentAccounts envWithAccounts) {
    List<EnvironmentAccount> inProgressAccounts =
        envWithAccounts.getEnvironmentAccounts().stream()
            .filter(
                environmentAccount -> environmentAccount.status().equals(TaskStatus.IN_PROGRESS))
            .toList();
    return inProgressAccounts.isEmpty()
        ? this.lockDao.releaseEnvironmentExclusiveLock(envWithAccounts.getEnvironment().id())
        : Completable.complete();
  }

  private Completable handleEnvironmentDeletion(
      EnvironmentEntityWithEnvironmentAccounts envWithAccounts) {
    return envWithAccounts.getEnvironment().status().equals(EnvironmentStatus.DELETED.name())
        ? this.environmentDao.updateEnvironmentAsInactive(envWithAccounts.getEnvironment().id())
        : Completable.complete();
  }
}
