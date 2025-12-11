package com.dream11.odin.service.operation;

import static com.dream11.odin.constant.Constants.EXTRA_ENV_VARS;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.constant.Action;
import com.dream11.odin.dao.ComponentTaskDao;
import com.dream11.odin.dao.ServiceTaskDao;
import com.dream11.odin.dto.ComponentData;
import com.dream11.odin.dto.requestqueue.ComponentAction;
import com.dream11.odin.dto.requestqueue.Stage;
import com.dream11.odin.dto.v1.ComponentDefinition;
import com.dream11.odin.dto.v1.ComponentProvisioningConfig;
import com.dream11.odin.entity.ComponentTaskEntity;
import com.dream11.odin.error.OdinError;
import com.dream11.odin.grpc.service.OperateServiceRequest;
import com.dream11.odin.service.ComponentEnrichmentService;
import com.dream11.odin.service.DatabasePollerService;
import com.dream11.odin.service.InterceptorService;
import com.dream11.odin.service.PlaceholderService;
import com.dream11.odin.util.ApplicationUtil;
import com.dream11.odin.util.ComponentUtil;
import com.dream11.odin.util.JsonUtil;
import com.dream11.odin.validations.ComponentOperationValidator;
import com.dream11.odin.validations.Validator;
import com.dream11.queue.producer.MessageProducer;
import com.google.inject.Inject;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ComponentOperation extends Operation {

  @Inject
  public ComponentOperation(
      ServiceTaskDao serviceTaskDao,
      ComponentTaskDao componentTaskDao,
      MysqlClient mysqlClient,
      DatabasePollerService databasePollerService,
      MessageProducer<String> messageProducer,
      PlaceholderService placeholderService,
      ComponentEnrichmentService componentEnrichmentService,
      InterceptorService interceptorService) {
    super(
        serviceTaskDao,
        componentTaskDao,
        mysqlClient,
        databasePollerService,
        messageProducer,
        placeholderService,
        componentEnrichmentService,
        interceptorService);
  }

  @Override
  public Validator inputValidator(
      OperateServiceRequest request,
      ServiceTaskDao serviceTaskDao,
      ComponentTaskDao componentTaskDao,
      Long envId) {
    return new ComponentOperationValidator(
        componentTaskDao, envId, request.getServiceName(), request.getComponentName());
  }

  private ComponentProvisioningConfig updateComponentProvisioningConfigWithExtraEnvVars(
      OperateServiceRequest request, ComponentProvisioningConfig componentProvisioningConfig) {
    JsonObject configJson = new JsonObject(request.getConfigJson());

    if (!configJson.containsKey(EXTRA_ENV_VARS)) {
      return componentProvisioningConfig.toBuilder().build();
    }

    io.vertx.core.json.JsonObject extraEnvVars =
        JsonUtil.convertProtoToJson(
            componentProvisioningConfig
                .getParams()
                .getFieldsMap()
                .getOrDefault(
                    EXTRA_ENV_VARS,
                    Value.newBuilder().setStructValue(Struct.getDefaultInstance()).build())
                .getStructValue());

    extraEnvVars.mergeIn(configJson.getJsonObject(EXTRA_ENV_VARS));

    Struct.Builder structBuilder = Struct.newBuilder();
    extraEnvVars.forEach(
        entry ->
            structBuilder.putFields(
                entry.getKey(),
                Value.newBuilder().setStringValue(entry.getValue().toString()).build()));

    Struct.Builder paramsBuilder = componentProvisioningConfig.getParams().toBuilder();
    paramsBuilder.putFields(
        EXTRA_ENV_VARS, Value.newBuilder().setStructValue(structBuilder.build()).build());

    return componentProvisioningConfig.toBuilder().setParams(paramsBuilder.build()).build();
  }

  private ComponentAction getComponentAction(
      OperateServiceRequest request, List<ComponentTaskEntity> previousComponentTaskEntities) {
    ComponentTaskEntity prevComponentTaskEntity =
        previousComponentTaskEntities.stream()
            .filter(
                componentTaskEntity ->
                    componentTaskEntity.getComponentName().equals(request.getComponentName()))
            .findFirst()
            .orElseThrow(
                () -> {
                  log.error("Component not found in previous tasks");
                  return ExceptionUtil.getException(OdinError.INTERNAL_SERVER_ERROR);
                });
    ComponentData componentData = ComponentUtil.getComponentData(prevComponentTaskEntity);
    ComponentDefinition componentDefinition = componentData.getComponentDefinition();
    ComponentProvisioningConfig componentProvisioningConfig =
        componentData.getComponentProvisioningConfig();
    ComponentProvisioningConfig newComponentProvisioningConfig =
        updateComponentProvisioningConfigWithExtraEnvVars(request, componentProvisioningConfig);

    return ComponentAction.builder()
        .id(ApplicationUtil.generateIntegerUUID())
        .componentName(request.getComponentName())
        .componentType(componentDefinition.getType())
        .componentVersion(componentDefinition.getVersion())
        .deploymentType(componentProvisioningConfig.getDeploymentType())
        .dependsOn(new ArrayList<>()) // currently operation does not have dependencies
        .baseConfig(JsonUtil.getMapFromProto(componentDefinition.getConfig()))
        .flavourConfig(JsonUtil.getMapFromProto(newComponentProvisioningConfig.getParams()))
        .operationConfig(JsonUtil.getMapFromJsonObjectString(request.getConfigJson()))
        .accounts(
            JsonUtil.getMapFromProto(
                componentData.getEnvironmentProviderAccounts().getServiceAccountsSnapshot()))
        .provider(
            componentData
                .getEnvironmentProviderAccounts()
                .getServiceAccountsSnapshot()
                .getAccount()
                .getProvider())
        .build();
  }

  ComponentAction getOperateComponentAction(
      OperateServiceRequest request, List<ComponentTaskEntity> previousComponentTaskEntities) {
    ComponentAction componentAction = getComponentAction(request, previousComponentTaskEntities);
    Stage operateStage =
        Stage.builder()
            .name(Action.OPERATE)
            .config(
                Map.of(
                    "stageName", Action.OPERATE.getName().toLowerCase(),
                    "operationName", request.getOperation()))
            .build();
    return componentAction.withStage(operateStage);
  }
}
