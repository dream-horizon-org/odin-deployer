package com.dream11.odin.util;

import com.dream11.odin.ApplicationContext;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dto.ComponentData;
import com.dream11.odin.dto.ComponentIdentifier;
import com.dream11.odin.dto.ServiceData;
import com.dream11.odin.dto.UserDetails;
import com.dream11.odin.dto.requestqueue.ComponentAction;
import com.dream11.odin.dto.requestqueue.Stage;
import com.dream11.odin.dto.v1.AccountInformation;
import com.dream11.odin.dto.v1.ComponentDefinition;
import com.dream11.odin.dto.v1.ComponentProvisioningConfig;
import com.dream11.odin.entity.ComponentTaskEntity;
import com.dream11.odin.entity.ServiceTaskEntity;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountResponse;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.codec.digest.DigestUtils;

@UtilityClass
public class ComponentUtil {

  public List<ComponentTaskEntity> createComponentTaskEntities(
      Map<ComponentIdentifier, ComponentData> componentDataMap,
      ServiceTaskEntity serviceTaskEntity,
      List<ComponentAction> componentActions,
      UserDetails userDetails) {
    Set<ComponentIdentifier> componentIdentifierSet = new HashSet<>(componentDataMap.keySet());
    List<ComponentTaskEntity> componentTaskEntities =
        new ArrayList<>(
            componentActions.stream()
                .map(
                    componentAction -> {
                      ComponentIdentifier componentIdentifier =
                          buildComponentId(
                              componentAction.getComponentName(),
                              componentAction.getStage().getName());
                      componentIdentifierSet.remove(componentIdentifier);
                      return createComponentTaskEntity(
                          componentDataMap.get(componentIdentifier),
                          serviceTaskEntity,
                          componentAction.getStage().getName(),
                          TaskStatus.IN_PROGRESS,
                          userDetails,
                          componentAction.getAccounts());
                    })
                .toList());

    // Add successful task entries for remaining component data whose component actions are not
    // present

    componentIdentifierSet.forEach(
        componentId ->
            componentTaskEntities.add(
                createComponentTaskEntity(
                    componentDataMap.get(componentId),
                    serviceTaskEntity,
                    componentId.getAction() != null ? componentId.getAction() : Action.DEPLOY,
                    TaskStatus.SUCCESSFUL,
                    userDetails,
                    JsonUtil.getMapFromProto(
                        componentDataMap
                            .get(componentId)
                            .getEnvironmentProviderAccounts()
                            .getServiceAccountsSnapshot()))));

    return componentTaskEntities;
  }

  public ComponentTaskEntity createComponentTaskEntity(
      ComponentData componentData,
      ServiceTaskEntity serviceTaskEntity,
      Action actions,
      TaskStatus status,
      UserDetails userDetails,
      Map<String, Object> accounts) {
    JsonObject componentConfig =
        componentConfigToJson(
            componentData.getComponentDefinition(),
            componentData.getComponentProvisioningConfig(),
            componentData.getOperationConfig());
    return ComponentTaskEntity.builder()
        .serviceTaskEntity(serviceTaskEntity)
        .componentName(componentData.getComponentDefinition().getName())
        .action(actions)
        .status(status)
        .config(componentConfig)
        .configHash(DigestUtils.sha256Hex(componentConfig.encode()))
        .version(1)
        .accounts(new JsonObject(accounts))
        .createdBy(userDetails.getUserId())
        .updatedBy(userDetails.getUserId())
        .build();
  }

  /***
   *
   * @param componentDataMap Map of componentData
   * @param componentsStageMap Map of (componentName, ComponentStage)
   * @return List of componentActions
   */
  public List<ComponentAction> getComponentActions(
      Map<ComponentIdentifier, ComponentData> componentDataMap,
      Map<String, Stage> componentsStageMap) {
    // Component Action ID is 4 digit random integer
    List<ComponentAction> componentActions =
        componentDataMap.entrySet().stream()
            .map(
                entry ->
                    ComponentAction.builder()
                        .id(ApplicationUtil.generateIntegerUUID())
                        .componentName(entry.getKey().getComponentName())
                        .componentType(entry.getValue().getComponentDefinition().getType())
                        .componentVersion(entry.getValue().getComponentDefinition().getVersion())
                        .baseConfig(
                            JsonUtil.getMapFromProto(
                                entry.getValue().getComponentDefinition().getConfig()))
                        .flavourConfig(
                            JsonUtil.getMapFromProto(
                                entry.getValue().getComponentProvisioningConfig().getParams()))
                        .deploymentType(
                            entry.getValue().getComponentProvisioningConfig().getDeploymentType())
                        .stage(componentsStageMap.get(entry.getKey().getComponentName()))
                        .provider(
                            entry
                                .getValue()
                                .getEnvironmentProviderAccounts()
                                .getServiceAccountsSnapshot()
                                .getAccount()
                                .getProvider())
                        .accounts(
                            JsonUtil.getMapFromProto(
                                entry
                                    .getValue()
                                    .getEnvironmentProviderAccounts()
                                    .getServiceAccountsSnapshot()))
                        .build())
            .toList();
    // Add component dependencies wrt component name
    return componentActions.stream()
        .map(
            componentAction -> {
              if (!List.of(Action.VALIDATE, Action.HEALTHCHECK, Action.UNDEPLOY)
                  .contains(componentAction.getStage().getName())) {
                return componentAction.addDependsOn(
                    getComponentDependencies(
                        buildComponentId(
                            componentAction.getComponentName(),
                            componentsStageMap.get(componentAction.getComponentName()).getName()),
                        componentDataMap,
                        componentActions));
              }
              return componentAction.addDependsOn(new ArrayList<>());
            })
        .toList();
  }

  public ComponentAction getComponentAction(
      ComponentDefinition componentDefinition,
      ComponentProvisioningConfig componentProvisioningConfig,
      AccountInformation accountInformation,
      Stage stage) {
    return ComponentAction.builder()
        .id(ApplicationUtil.generateIntegerUUID())
        .componentName(componentDefinition.getName())
        .componentType(componentDefinition.getType())
        .componentVersion(componentDefinition.getVersion())
        .baseConfig(JsonUtil.getMapFromProto(componentDefinition.getConfig()))
        .flavourConfig(JsonUtil.getMapFromProto(componentProvisioningConfig.getParams()))
        .deploymentType(componentProvisioningConfig.getDeploymentType())
        .provider(accountInformation.getServiceAccountsSnapshot().getAccount().getProvider())
        // Depends on to be added after all actions are generated
        .accounts(JsonUtil.getMapFromProto(accountInformation.getServiceAccountsSnapshot()))
        .dependsOn(new ArrayList<>())
        .stage(stage)
        .build();
  }

  /**
   * Returns componentAction for a given componentIdentifier
   *
   * @param componentIdentifier componentIdentifier (componentName, componentStage)
   * @param componentActions List of componentActions where each action has unique component Name
   * @return componentAction
   */
  public ComponentAction getComponentAction(
      ComponentIdentifier componentIdentifier, List<ComponentAction> componentActions) {
    return componentActions.stream()
        .filter(
            componentAction ->
                componentAction.getComponentName().equals(componentIdentifier.getComponentName()))
        .filter(
            componentAction ->
                componentAction.getStage().getName().equals(componentIdentifier.getAction()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "Component action not found for component [%s]",
                        componentIdentifier.getComponentName())));
  }

  public List<Integer> getComponentDependencies(
      ComponentIdentifier componentIdentifier,
      Map<ComponentIdentifier, ComponentData> componentDataMap,
      List<ComponentAction> componentActions) {
    return componentDataMap
        .get(componentIdentifier)
        .getComponentDefinition()
        .getDependsOnList()
        .stream()
        .map(dependentComponent -> getComponentActionId(dependentComponent, componentActions))
        .toList();
  }

  public Integer getComponentActionId(
      String componentName, List<ComponentAction> componentActions) {
    return componentActions.stream()
        .filter(componentAction -> componentAction.getComponentName().equals(componentName))
        .findFirst()
        .map(ComponentAction::getId)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    String.format("Component Action not found for component %s", componentName)));
  }

  public Map<String, Stage> generateAllComponentStages(
      List<String> componentNames, Action action, Map<String, Object> stageConfig) {
    return componentNames.stream()
        .collect(
            Collectors.toMap(
                componentName -> componentName,
                componentName -> Stage.builder().name(action).config(stageConfig).build()));
  }

  public JsonArray componentProvisioningConfigsToJsonArray(
      List<ComponentProvisioningConfig> componentProvisioningConfigs) {
    return new JsonArray(
        componentProvisioningConfigs.stream()
            .map(
                componentProvisioningConfig -> {
                  try {
                    return new JsonObject(
                        JsonFormat.printer()
                            .preservingProtoFieldNames()
                            .print(componentProvisioningConfig));
                  } catch (InvalidProtocolBufferException e) {
                    throw new IllegalArgumentException(
                        "Failed to convert component provisioning config to Json {}", e.getCause());
                  }
                })
            .toList());
  }

  public JsonObject componentConfigToJson(
      ComponentDefinition componentDefinition,
      ComponentProvisioningConfig componentProvisioningConfig,
      Struct operationConfig) {
    Map<String, Object> componentConfigMap =
        Map.of(
            Constants.COMPONENT_CONFIG_KEY,
            JsonUtil.getJsonFromProto(componentDefinition),
            Constants.PROVISIONING_CONFIG_KEY,
            JsonUtil.getJsonFromProto(componentProvisioningConfig));
    if (operationConfig != null) {
      componentConfigMap.put(
          Constants.OPERATION_CONFIG_KEY, JsonUtil.getJsonFromProto(operationConfig));
    }
    return new JsonObject(componentConfigMap);
  }

  public ComponentProvisioningConfig getComponentProvisioningConfig(
      List<ComponentProvisioningConfig> componentProvisioningConfigs, String componentName) {
    return componentProvisioningConfigs.stream()
        .filter(
            componentProvisioningConfig ->
                componentProvisioningConfig.getComponentName().equals(componentName))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    String.format(
                        "Component provisioning config for component %s not found in provisioning config",
                        componentName)));
  }

  public ComponentDefinition getComponentDefinitionFromJson(JsonObject jsonObject) {
    ComponentDefinition.Builder builder = ComponentDefinition.newBuilder();
    try {
      JsonFormat.parser().merge(jsonObject.toString(), builder);
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalArgumentException("Failed to parse json to component definition");
    }
    return builder.build();
  }

  public ComponentProvisioningConfig getComponentProvisioningConfigFromJson(JsonObject jsonObject) {
    ComponentProvisioningConfig.Builder builder = ComponentProvisioningConfig.newBuilder();
    try {
      JsonFormat.parser().merge(jsonObject.toString(), builder);
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalArgumentException("Failed to json struct to component provisioning config");
    }
    return builder.build();
  }

  /** //todo: can be deleted */
  public Map<ComponentIdentifier, ComponentData> getAllComponentsData(
      ServiceData serviceData,
      List<AccountInformation> accountInformationList,
      Map<String, Action> componentActionMap) {
    return serviceData.getServiceDefinition().getComponentsList().stream()
        .collect(
            Collectors.toMap(
                componentDefinition ->
                    buildComponentId(
                        componentDefinition.getName(),
                        componentActionMap.get(componentDefinition.getName())),
                componentDefinition -> {
                  ComponentProvisioningConfig componentProvisioningConfig =
                      getComponentProvisioningConfig(
                          serviceData.getComponentProvisioningConfigs(),
                          componentDefinition.getName());
                  return ComponentData.builder()
                      .componentDefinition(componentDefinition)
                      .environmentProviderAccounts(
                          AccountUtils.filterAccount(
                              accountInformationList,
                              componentProvisioningConfig.getDeploymentType()))
                      .componentProvisioningConfig(componentProvisioningConfig)
                      .build();
                }));
  }

  public Map<ComponentIdentifier, ComponentData> buildComponentsData(
      ServiceData serviceData, Action action, List<ComponentTaskEntity> componentTaskEntities) {
    return serviceData.getServiceDefinition().getComponentsList().stream()
        .collect(
            Collectors.toMap(
                componentDefinition -> buildComponentId(componentDefinition.getName(), action),
                componentDefinition ->
                    ComponentData.builder()
                        .componentDefinition(componentDefinition)
                        .environmentProviderAccounts(
                            componentTaskEntities.stream()
                                .filter(
                                    componentTaskEntity ->
                                        componentTaskEntity
                                            .getComponentName()
                                            .equals(componentDefinition.getName()))
                                .findFirst()
                                .map(
                                    componentTaskEntity ->
                                        AccountUtils.getAccountInformation(
                                            componentTaskEntity.getAccounts()))
                                .orElseThrow())
                        .componentProvisioningConfig(
                            getComponentProvisioningConfig(
                                serviceData.getComponentProvisioningConfigs(),
                                componentDefinition.getName()))
                        .build()));
  }

  public ComponentIdentifier buildComponentId(String componentName, Action action) {
    return ComponentIdentifier.builder().componentName(componentName).action(action).build();
  }

  public Set<String> getUnexecutedComponentNameSet(
      List<ComponentTaskEntity> componentTaskEntities) {
    Set<String> unexecutedComponents = new HashSet<>();
    // Build Dag
    Map<String, List<String>> taskGraph = buildTaskGraph(componentTaskEntities);

    // Iterate collect descendent nodes of failed tasks
    componentTaskEntities.forEach(
        componentTaskEntity -> {
          if (componentTaskEntity.getStatus().equals(TaskStatus.FAILED)
              && taskGraph.containsKey(componentTaskEntity.getComponentName())) {
            unexecutedComponents.addAll(taskGraph.get(componentTaskEntity.getComponentName()));
          }
        });

    return unexecutedComponents;
  }

  private Map<String, List<String>> buildTaskGraph(
      List<ComponentTaskEntity> componentTaskEntities) {
    Map<String, List<String>> taskGraph = new HashMap<>();

    componentTaskEntities.forEach(
        componentTaskEntity ->
            getDependentComponentTask(componentTaskEntity)
                .forEach(
                    node ->
                        taskGraph
                            .computeIfAbsent(node, k -> new ArrayList<>())
                            .add(componentTaskEntity.getComponentName())));

    return taskGraph;
  }

  private List<String> getDependentComponentTask(ComponentTaskEntity componentTaskEntity) {
    return ComponentUtil.getComponentDefinitionFromJson(
            componentTaskEntity.getConfig().getJsonObject(Constants.COMPONENT_CONFIG_KEY))
        .getDependsOnList();
  }

  public static Map<ComponentIdentifier, ComponentData> getComponentDataMapForAction(
      Map<ComponentIdentifier, ComponentData> componentDataMap, Action action) {
    return componentDataMap.entrySet().stream()
        .map(entry -> Map.entry(entry.getKey().withAction(action), entry.getValue()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public ComponentData getComponentData(ComponentAction componentAction) {
    return ComponentData.builder()
        .componentDefinition(
            ComponentDefinition.newBuilder()
                .setName(componentAction.getComponentName())
                .setType(componentAction.getComponentType())
                .setVersion(componentAction.getComponentVersion())
                .setConfig(
                    JsonUtil.jsonToProtoBuilder(
                            new JsonObject(componentAction.getBaseConfig()), Struct.newBuilder())
                        .build())
                .build())
        .componentProvisioningConfig(
            ComponentProvisioningConfig.newBuilder()
                .setComponentName(componentAction.getComponentName())
                .setDeploymentType(componentAction.getDeploymentType())
                .setParams(
                    JsonUtil.jsonToProtoBuilder(
                            new JsonObject(componentAction.getFlavourConfig()), Struct.newBuilder())
                        .build())
                .build())
        .operationConfig(
            JsonUtil.jsonToProtoBuilder(
                    new JsonObject(componentAction.getOperationConfig()), Struct.newBuilder())
                .build())
        .environmentProviderAccounts(
            JsonUtil.jsonToProtoBuilder(
                    new JsonObject(componentAction.getAccounts()), AccountInformation.newBuilder())
                .build())
        .build();
  }

  public ComponentData getComponentData(ComponentTaskEntity componentTaskEntity) {
    GetProviderAccountResponse providerAccountResponse =
        JsonUtil.jsonToProtoBuilder(
                componentTaskEntity.getAccounts(), GetProviderAccountResponse.newBuilder())
            .build();
    AccountInformation accountInformation =
        AccountInformation.newBuilder()
            .setServiceAccountsSnapshot(providerAccountResponse)
            .setProviderAccountName(providerAccountResponse.getAccount().getName())
            .build();
    Struct.Builder operationConfigBuilder = Struct.newBuilder();
    if (componentTaskEntity.getConfig().containsKey(Constants.OPERATION_CONFIG_KEY)) {
      operationConfigBuilder.putAllFields(
          JsonUtil.jsonToProtoBuilder(
                  componentTaskEntity.getConfig().getJsonObject(Constants.OPERATION_CONFIG_KEY),
                  Struct.newBuilder())
              .build()
              .getFieldsMap());
    }
    return ComponentData.builder()
        .componentDefinition(
            JsonUtil.jsonToProtoBuilder(
                    componentTaskEntity.getConfig().getJsonObject(Constants.COMPONENT_CONFIG_KEY),
                    ComponentDefinition.newBuilder())
                .build())
        .componentProvisioningConfig(
            JsonUtil.jsonToProtoBuilder(
                    componentTaskEntity
                        .getConfig()
                        .getJsonObject(Constants.PROVISIONING_CONFIG_KEY),
                    ComponentProvisioningConfig.newBuilder())
                .build())
        .operationConfig(operationConfigBuilder.build())
        .environmentProviderAccounts(accountInformation)
        .build();
  }

  public static ComponentTaskEntity getComponentTaskEntity(
      ComponentAction componentAction, ServiceTaskEntity serviceTaskEntity) {
    UserDetails userDetails = ApplicationContext.getUserDetails();
    ComponentData componentData = getComponentData(componentAction);
    JsonObject config =
        componentConfigToJson(
            componentData.getComponentDefinition(),
            componentData.getComponentProvisioningConfig(),
            componentData.getOperationConfig());

    return ComponentTaskEntity.builder()
        .serviceTaskEntity(serviceTaskEntity)
        .componentName(componentAction.getComponentName())
        .config(config)
        .configHash(DigestUtils.sha256Hex(config.toString()))
        .status(TaskStatus.IN_PROGRESS)
        .action(componentAction.getStage().getName())
        .accounts(new JsonObject(componentAction.getAccounts()))
        .version(1)
        .createdBy(userDetails.getUserId())
        .updatedBy(userDetails.getUserId())
        .build();
  }

  public static List<ComponentTaskEntity> filterComponentTaskEntitiesForOperate(
      List<ComponentTaskEntity> componentTaskEntities,
      String operatedComponentName,
      ServiceTaskEntity newServiceTaskEntity) {
    return componentTaskEntities.stream()
        .filter(
            componentTaskEntity ->
                // remove undeploy successful tasks
                !(componentTaskEntity.getAction().equals(Action.UNDEPLOY)
                    && componentTaskEntity.getStatus().equals(TaskStatus.SUCCESSFUL)))
        .filter(
            componentTaskEntity ->
                // remove operated component old task
                !componentTaskEntity.getComponentName().equals(operatedComponentName))
        .map(componentTaskEntity -> componentTaskEntity.withServiceTaskEntity(newServiceTaskEntity))
        .toList();
  }
}
