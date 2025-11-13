package com.dream11.odin.service.operation;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.constant.Action;
import com.dream11.odin.dao.ComponentTaskDao;
import com.dream11.odin.dao.ServiceTaskDao;
import com.dream11.odin.dto.requestqueue.ComponentAction;
import com.dream11.odin.dto.requestqueue.Stage;
import com.dream11.odin.dto.v1.AccountInformation;
import com.dream11.odin.dto.v1.AddComponentRequestOptions;
import com.dream11.odin.dto.v1.ComponentDefinition;
import com.dream11.odin.dto.v1.ComponentProvisioningConfig;
import com.dream11.odin.entity.ComponentTaskEntity;
import com.dream11.odin.error.OdinError;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountResponse;
import com.dream11.odin.grpc.service.OperateServiceRequest;
import com.dream11.odin.service.ComponentEnrichmentService;
import com.dream11.odin.service.DatabasePollerService;
import com.dream11.odin.service.InterceptorService;
import com.dream11.odin.service.PlaceholderService;
import com.dream11.odin.util.ApplicationUtil;
import com.dream11.odin.util.JsonUtil;
import com.dream11.odin.validations.AddComponentValidator;
import com.dream11.odin.validations.Validator;
import com.dream11.queue.producer.MessageProducer;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AddComponentServiceOperation extends ServiceOperation {

  @Inject
  protected AddComponentServiceOperation(
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
    AddComponentRequestOptions addComponentRequest =
        JsonUtil.jsonToProtoBuilder(
                JsonUtil.getJsonFromProto(request.getConfig()),
                AddComponentRequestOptions.newBuilder())
            .build();
    return new AddComponentValidator(
        componentTaskDao,
        envId,
        request.getServiceName(),
        addComponentRequest.getComponentDefinition(0).getName());
  }

  private ComponentAction getComponentAction(
      OperateServiceRequest request, List<ComponentTaskEntity> previousComponentTaskEntities) {
    AddComponentRequestOptions addComponentRequest =
        JsonUtil.jsonToProtoBuilder(
                JsonUtil.getJsonFromProto(request.getConfig()),
                AddComponentRequestOptions.newBuilder())
            .build();
    // Currently adding only single component is support
    ComponentDefinition componentDefinition =
        addComponentRequest.getComponentDefinitionList().get(0);
    ComponentProvisioningConfig componentProvisioningConfig =
        addComponentRequest.getProvisioningConfigList().stream()
            .filter(
                inputComponentProvisioningConfig ->
                    inputComponentProvisioningConfig
                        .getComponentName()
                        .equals(componentDefinition.getName()))
            .findFirst()
            .orElseThrow(
                () ->
                    ExceptionUtil.getException(
                        OdinError.COMPONENT_NOT_FOUND_IN_OPERATION_INPUT,
                        componentDefinition.getName()));
    GetProviderAccountResponse providerAccountResponse;
    if (previousComponentTaskEntities.isEmpty()) {
      // Get account data from the environment
      // TODO: Implement this
    }
    providerAccountResponse =
        JsonUtil.jsonToProtoBuilder(
                previousComponentTaskEntities.get(0).getAccounts(),
                GetProviderAccountResponse.newBuilder())
            .build();
    AccountInformation accountInformation =
        AccountInformation.newBuilder()
            .setServiceAccountsSnapshot(providerAccountResponse)
            .setProviderAccountName(providerAccountResponse.getAccount().getName())
            .build();
    return ComponentAction.builder()
        .id(ApplicationUtil.generateIntegerUUID())
        .componentName(componentDefinition.getName())
        .componentType(componentDefinition.getType())
        .componentVersion(componentDefinition.getVersion())
        .deploymentType(componentProvisioningConfig.getDeploymentType())
        .dependsOn(new ArrayList<>()) // currently operation does not have dependencies
        .baseConfig(JsonUtil.getMapFromProto(componentDefinition.getConfig()))
        .flavourConfig(JsonUtil.getMapFromProto(componentProvisioningConfig.getParams()))
        .operationConfig(
            new HashMap<>()) // Operation config is not required for add component operation
        .accounts(JsonUtil.getMapFromProto(accountInformation.getServiceAccountsSnapshot()))
        .provider(accountInformation.getServiceAccountsSnapshot().getAccount().getProvider())
        .build();
  }

  @Override
  ComponentAction getOperateComponentAction(
      OperateServiceRequest request, List<ComponentTaskEntity> previousComponentTaskEntities) {
    ComponentAction componentAction = getComponentAction(request, previousComponentTaskEntities);
    Stage stage = Stage.builder().name(Action.DEPLOY).config(new HashMap<>()).build();
    return componentAction.withStage(stage);
  }
}
