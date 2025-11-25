package com.dream11.odin.validations;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dao.ServiceComponentDao;
import com.dream11.odin.entity.ComponentEntity;
import com.dream11.odin.entity.EnvironmentServiceEntityWithComponents;
import com.dream11.odin.error.OdinError;
import io.reactivex.Completable;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ServiceStatusValidatorForUndeploy extends Validator {

  private final ServiceComponentDao serviceComponentDao;
  private final String serviceName;
  private final String envName;

  private final Long orgId;

  // if service is in deploy success or failed, or operate success or failed, or undeploy failed, it
  // should be eligible for undeploy

  @Override
  public Completable validate() {
    return serviceComponentDao
        .getServiceComponentStateInEnv(orgId, envName, serviceName)
        .map(
            environmentServiceEntityWithComponents -> {
              if (validateServiceTaskStatusForUndeploy(environmentServiceEntityWithComponents)) {
                return Completable.complete();
              }
              throw ExceptionUtil.getException(
                  OdinError.SERVICE_CANNOT_BE_UNDEPLOYED,
                  serviceName,
                  environmentServiceEntityWithComponents
                      .getEnvironmentServiceEntity()
                      .getServiceAction(),
                  environmentServiceEntityWithComponents
                      .getEnvironmentServiceEntity()
                      .getServiceStatus());
            })
        .ignoreElement();
  }

  private boolean validateServiceTaskStatusForUndeploy(
      EnvironmentServiceEntityWithComponents environmentServiceEntityWithComponents) {
    return environmentServiceEntityWithComponents.getComponents().stream()
        .anyMatch(this::invalidComponentStatusChecker);
  }

  private boolean invalidComponentStatusChecker(ComponentEntity componentEntity) {
    return componentEntity.getStatus().equals(TaskStatus.IN_PROGRESS)
        || componentEntity.getAction().equals(Action.UNDEPLOY)
            & componentEntity.getStatus().equals(TaskStatus.SUCCESSFUL);
  }
}
