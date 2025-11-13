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
public class RemoveComponentValidator extends Validator {
  private final ComponentTaskDao componentTaskDao;
  private final long envId;
  private final String serviceName;
  private final String operatedComponentName;

  /*
   Should validate that the component must be present in service with following action and state
   - action deploy and status successful or failed
   - action operate and status successful or failed
   - action undeploy and status failed
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
              boolean statusSuccessOrFailed =
                  componentStatus.equals(TaskStatus.SUCCESSFUL)
                      || componentStatus.equals(TaskStatus.FAILED);
              if (((componentAction.equals(Action.DEPLOY) || componentAction.equals(Action.OPERATE))
                      && statusSuccessOrFailed)
                  || (componentAction.equals(Action.UNDEPLOY)
                      && (componentStatus.equals(TaskStatus.FAILED)
                          || componentStatus.equals(TaskStatus.IN_PROGRESS)))) {
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
