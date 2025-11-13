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
public class AddComponentValidator extends Validator {
  private final ComponentTaskDao componentTaskDao;
  private final long envId;
  private final String serviceName;
  private final String operatedComponentName;

  /*
   Should validate that the component is present in both component definition and provision config
   Should validate that the component is either not present or else present with
   - action deploy and status failed OR
   - action undeploy and status successful
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
                      .orElse(null);
              // Component is missing
              if (filteredComponentDataStatus == null) {
                return Completable.complete();
              }
              Action componentAction = filteredComponentDataStatus.getAction();
              TaskStatus componentStatus = filteredComponentDataStatus.getStatus();
              if ((componentAction.equals(Action.DEPLOY)
                      && (componentStatus.equals(TaskStatus.FAILED)
                          || componentStatus.equals(TaskStatus.IN_PROGRESS)))
                  || (componentAction.equals(Action.UNDEPLOY)
                      && componentStatus.equals(TaskStatus.SUCCESSFUL))) {
                return Completable.complete();
              }
              throw ExceptionUtil.getException(
                  OdinError.COMPONENT_CANNOT_BE_ADDED,
                  operatedComponentName,
                  componentAction.toString(),
                  componentStatus.toString());
            })
        .ignoreElement();
  }
}
