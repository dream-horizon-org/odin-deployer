package com.dream11.odin.validations;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dao.ServiceTaskDao;
import com.dream11.odin.error.OdinError;
import io.reactivex.Completable;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ServiceStatusValidatorForOperate extends Validator {
  private final ServiceTaskDao serviceTaskDao;
  private final String serviceName;
  private final Long envId;

  @Override
  public Completable validate() {
    return serviceTaskDao
        .getServiceStatusExcludingHealthcheck(serviceName, envId)
        .map(
            actionTaskStatusPair -> {
              if (validateServiceTaskStatusForOperate(
                  actionTaskStatusPair.getLeft(), actionTaskStatusPair.getRight())) {
                return Completable.complete();
              }
              throw ExceptionUtil.getException(
                  OdinError.SERVICE_CANNOT_BE_OPERATED,
                  serviceName,
                  actionTaskStatusPair.getLeft().toString(),
                  actionTaskStatusPair.getRight().toString());
            })
        .ignoreElement();
  }

  private boolean validateServiceTaskStatusForOperate(Action serviceAction, TaskStatus status) {
    return serviceAction.equals(Action.DEPLOY) && status.equals(TaskStatus.SUCCESSFUL)
        || serviceAction.equals(Action.OPERATE);
  }
}
