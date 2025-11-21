package com.dream11.odin.service.operation;

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
import com.dream11.odin.dto.v1.RemoveComponentRequestOptions;
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
import com.dream11.odin.validations.RemoveComponentValidator;
import com.dream11.odin.validations.Validator;
import com.dream11.queue.producer.MessageProducer;
import com.google.inject.Inject;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RemoveComponentServiceOperation extends ServiceOperation {

  @Inject
  protected RemoveComponentServiceOperation(
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
    RemoveComponentRequestOptions removeComponentRequest =
        JsonUtil.jsonToProtoBuilder(
                new JsonObject(request.getConfigJson()), RemoveComponentRequestOptions.newBuilder())
            .build();
    return new RemoveComponentValidator(
        componentTaskDao,
        envId,
        request.getServiceName(),
        removeComponentRequest.getComponentName());
  }

  private ComponentAction getComponentAction(
      OperateServiceRequest request, List<ComponentTaskEntity> previousComponentTaskEntities) {
    RemoveComponentRequestOptions removeComponentRequest =
        JsonUtil.jsonToProtoBuilder(
                new JsonObject(request.getConfigJson()), RemoveComponentRequestOptions.newBuilder())
            .build();
    ComponentTaskEntity prevComponentTaskEntity =
        previousComponentTaskEntities.stream()
            .filter(
                componentTaskEntity ->
                    componentTaskEntity
                        .getComponentName()
                        .equals(removeComponentRequest.getComponentName()))
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
    return ComponentAction.builder()
        .id(ApplicationUtil.generateIntegerUUID())
        .componentName(prevComponentTaskEntity.getComponentName())
        .componentType(componentDefinition.getType())
        .componentVersion(componentDefinition.getVersion())
        .deploymentType(componentProvisioningConfig.getDeploymentType())
        .dependsOn(new ArrayList<>()) // currently operation does not have dependencies
        .baseConfig(JsonUtil.getMapFromProto(componentDefinition.getConfig()))
        .flavourConfig(JsonUtil.getMapFromProto(componentProvisioningConfig.getParams()))
        .operationConfig(new HashMap<>())
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
    Stage stage = Stage.builder().name(Action.UNDEPLOY).config(new HashMap<>()).build();
    return componentAction.withStage(stage);
  }
}
