package com.dream11.odin.validations;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dao.ComponentTaskDao;
import com.dream11.odin.dto.ComponentDataStatus;
import com.dream11.odin.error.OdinError;
import io.reactivex.Completable;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ComponentOperationValidator extends Validator {
  private final ComponentTaskDao componentTaskDao;
  private final Long envId;
  private final String serviceName;
  private final String operatedComponentName;

  /*
   Should validate that the component must exist in service with following action and state
   - Deployed and successful status
   - Operated with successful or failed status
  */
  @Override
  public Completable validate() {

    return componentTaskDao
        .getComponentsDataAndStatusExcludingHealthcheckInServiceAndEnv(serviceName, envId)
        .map(
            componentDataStatuses -> {
              ComponentDataStatus filteredComponentDataStatus =
                  componentDataStatuses.stream()
                      .filter(
                          componentDataStatus ->
                              componentDataStatus
                                  .getComponentData()
                                  .getComponentDefinition()
                                  .getName()
                                  .equals(operatedComponentName))
                      .findFirst()
                      .orElseThrow(
                          () ->
                              ExceptionUtil.getException(
                                  OdinError.COMPONENT_DOES_NOT_EXIST_IN_SERVICE,
                                  operatedComponentName,
                                  serviceName));
              Action componentAction = filteredComponentDataStatus.getAction();
              TaskStatus componentStatus = filteredComponentDataStatus.getStatus();
              if ((componentAction.equals(Action.DEPLOY)
                      && componentStatus.equals(TaskStatus.SUCCESSFUL))
                  || (componentAction.equals(Action.OPERATE))) {
                return Completable.complete();
              }
              throw ExceptionUtil.getException(
                  OdinError.COMPONENT_CANNOT_BE_OPERATED,
                  operatedComponentName,
                  componentAction.toString(),
                  componentStatus.toString());
            })
        .ignoreElement();
  }
}
