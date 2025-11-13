package com.dream11.odin.validations;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dao.ServiceTaskDao;
import com.dream11.odin.error.OdinError;
import io.reactivex.Completable;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ServiceStatusValidatorForUndeploy extends Validator {
  private final ServiceTaskDao serviceTaskDao;
  private final String serviceName;
  private final Long envId;

  @Override
  public Completable validate() {
    return serviceTaskDao
        .getServiceStatusExcludingHealthcheck(serviceName, envId)
        .map(
            actionTaskStatusPair -> {
              if (validateServiceTaskStatusForUndeploy(
                  serviceName, actionTaskStatusPair.getLeft(), actionTaskStatusPair.getRight())) {
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

  private boolean validateServiceTaskStatusForUndeploy(
      String serviceName, Action serviceAction, TaskStatus status) {
    if (((serviceAction.equals(Action.DEPLOY) || serviceAction.equals(Action.OPERATE))
            && (status.equals(TaskStatus.SUCCESSFUL) || status.equals(TaskStatus.FAILED)))
        || (serviceAction.equals(Action.UNDEPLOY))) {
      return true;
    }
    throw ExceptionUtil.getException(
        OdinError.SERVICE_CANNOT_BE_UNDEPLOYED,
        serviceName,
        serviceAction.toString(),
        status.toString());
  }
}
