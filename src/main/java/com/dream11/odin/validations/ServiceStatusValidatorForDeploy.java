package com.dream11.odin.validations;

import static com.dream11.odin.constant.Action.*;

import com.dream11.grpc.error.GrpcException;
import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dao.ServiceComponentDao;
import com.dream11.odin.dto.ComponentData;
import com.dream11.odin.dto.ComponentIdentifier;
import com.dream11.odin.dto.ServiceData;
import com.dream11.odin.entity.EnvironmentServiceEntityWithComponents;
import com.dream11.odin.error.OdinError;
import com.dream11.odin.util.JsonUtil;
import io.reactivex.Completable;
import io.vertx.core.json.JsonObject;
import java.util.Map;
import java.util.Objects;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ServiceStatusValidatorForDeploy extends Validator {

  private final ServiceComponentDao serviceComponentDao;
  private final String envName;
  Map<ComponentIdentifier, ComponentData> componentDataMap;
  private final ServiceData serviceData;
  private final long orgId;

  @Override
  public Completable validate() {
    return serviceComponentDao
        .getServiceComponentStateInEnv(orgId, envName, serviceData.getServiceDefinition().getName())
        .flatMapCompletable(
            environmentServiceEntityWithComponents -> {
              if (!validateServiceTaskStatusForDeploy(
                  environmentServiceEntityWithComponents
                      .getEnvironmentServiceEntity()
                      .getServiceAction(),
                  environmentServiceEntityWithComponents
                      .getEnvironmentServiceEntity()
                      .getServiceStatus())) {
                return Completable.error(
                    getError(
                        environmentServiceEntityWithComponents
                            .getEnvironmentServiceEntity()
                            .getServiceAction(),
                        environmentServiceEntityWithComponents
                            .getEnvironmentServiceEntity()
                            .getServiceStatus()));
              }

              return Completable.fromAction(
                  () ->
                      compareServiceAndComponentConfigs(
                          componentDataMap, environmentServiceEntityWithComponents));
            });
  }

  private boolean validateServiceTaskStatusForDeploy(Action serviceAction, TaskStatus status) {
    return serviceAction.equals(DEPLOY) && status.equals(TaskStatus.FAILED)
        || serviceAction.equals(UNDEPLOY) && status.equals(TaskStatus.SUCCESSFUL)
        || serviceAction.equals(VALIDATE) && status.equals(TaskStatus.FAILED);
  }

  private void compareServiceAndComponentConfigs(
      Map<ComponentIdentifier, ComponentData> componentDataMap,
      EnvironmentServiceEntityWithComponents environmentServiceEntityWithComponents) {

    environmentServiceEntityWithComponents.getComponents().stream()
        .map(
            componentEntity -> {
              ComponentIdentifier compId = getComponentId(componentEntity.getName());
              ComponentData componentData = componentDataMap.get(compId);

              if (!componentDataMap.containsKey(
                  compId)) { // todo: later we should allow failed components to be removed
                return String.format(
                    "Cannot remove component until deployment completes '%s'",
                    componentEntity.getName());
              }

              if (componentEntity.getStatus() == TaskStatus.SUCCESSFUL
                  && isConfigModified(componentData, componentEntity.getConfig())) {
                return String.format(
                    "Cannot modify successfully deployed component '%s'",
                    componentEntity.getName());
              }

              if (componentEntity.getStatus() == TaskStatus.FAILED
                  && isOdinConfigModified(componentData, componentEntity.getConfig())) {
                return String.format(
                    "Cannot modify type or version of failed component '%s'",
                    componentEntity.getName());
              }

              return null;
            })
        .filter(Objects::nonNull)
        .findAny()
        .ifPresent(
            errorMsg -> {
              throw ExceptionUtil.getException(
                  OdinError.INVALID_ACTION, errorMsg + " while service hasn't deployed fully");
            });
  }

  private boolean isOdinConfigModified(ComponentData componentData, JsonObject componentConfig) {
    return !componentData
            .getComponentDefinition()
            .getType()
            .equals(componentConfig.getJsonObject("componentConfig").getString("type"))
        || !componentData
            .getComponentDefinition()
            .getVersion()
            .equals(componentConfig.getJsonObject("componentConfig").getString("version"));
  }

  private boolean isConfigModified(ComponentData componentData, JsonObject componentConfig) {

    if (isOdinConfigModified(componentData, componentConfig)) return true;

    JsonObject oldDefinitionConfig =
        componentConfig.getJsonObject("componentConfig").getJsonObject("config");
    JsonObject oldProvisioningConfig =
        componentConfig.getJsonObject("provisioningConfig").getJsonObject("params");

    JsonObject newDefinitionConfig =
        JsonUtil.convertProtoToJson(componentData.getComponentDefinition().getConfig());
    JsonObject newProvisioningConfig =
        JsonUtil.convertProtoToJson(componentData.getComponentProvisioningConfig().getParams());

    Map<String, Object> oldDefMap = JsonUtil.toMap(oldDefinitionConfig);
    Map<String, Object> newDefMap = JsonUtil.toMap(newDefinitionConfig);

    Map<String, Object> oldProvMap = JsonUtil.toMap(oldProvisioningConfig);
    Map<String, Object> newProvMap = JsonUtil.toMap(newProvisioningConfig);

    boolean definitionChanged = !Objects.equals(oldDefMap, newDefMap);
    boolean provisioningChanged = !Objects.equals(oldProvMap, newProvMap);

    return definitionChanged || provisioningChanged;
  }

  private ComponentIdentifier getComponentId(String componentName) {
    return ComponentIdentifier.builder().action(DEPLOY).componentName(componentName).build();
  }

  private GrpcException getError(Action serviceAction, TaskStatus status) {
    switch (serviceAction) {
      case DEPLOY -> {
        return ExceptionUtil.getException(
            OdinError.CANNOT_DEPLOY_OVER_ACTION_WITH_STATUS,
            serviceData.getServiceDefinition().getName(),
            serviceAction,
            status,
            status.equals(TaskStatus.SUCCESSFUL)
                ? Constants.USE_OPERATE
                : Constants.WAIT_FOR_DEPLOYMENT);
      }

      case UNDEPLOY -> {
        return ExceptionUtil.getException(
            OdinError.CANNOT_DEPLOY_OVER_ACTION_WITH_STATUS,
            serviceData.getServiceDefinition().getName(),
            serviceAction,
            status,
            Constants.UNDEPLOY_AGAIN);
      }

      case OPERATE -> {
        return ExceptionUtil.getException(
            OdinError.CANNOT_DEPLOY_OVER_ACTION_WITH_STATUS,
            serviceData.getServiceDefinition().getName(),
            serviceAction,
            status,
            Constants.USE_OPERATE);
      }
    }
    return ExceptionUtil.getException(OdinError.INVALID_ACTION, serviceAction);
  }
}
