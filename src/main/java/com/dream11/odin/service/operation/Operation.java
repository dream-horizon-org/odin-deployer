package com.dream11.odin.service.operation;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.ApplicationContext;
import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.constant.ServiceOperations;
import com.dream11.odin.dao.ComponentTaskDao;
import com.dream11.odin.dao.ServiceTaskDao;
import com.dream11.odin.dto.ComponentData;
import com.dream11.odin.dto.ComponentIdentifier;
import com.dream11.odin.dto.RequestMetaContext;
import com.dream11.odin.dto.UserDetails;
import com.dream11.odin.dto.requestqueue.ComponentAction;
import com.dream11.odin.dto.requestqueue.ServiceRequestQueueMessage;
import com.dream11.odin.dto.v1.AddComponentRequestOptions;
import com.dream11.odin.dto.v1.ComponentDefinition;
import com.dream11.odin.dto.v1.Environment;
import com.dream11.odin.dto.v1.RemoveComponentRequestOptions;
import com.dream11.odin.entity.ComponentTaskEntity;
import com.dream11.odin.entity.ServiceTaskEntity;
import com.dream11.odin.error.OdinError;
import com.dream11.odin.grpc.service.OperateServiceRequest;
import com.dream11.odin.grpc.service.OperateServiceResponse;
import com.dream11.odin.service.ComponentEnrichmentService;
import com.dream11.odin.service.DatabasePollerService;
import com.dream11.odin.service.InterceptorService;
import com.dream11.odin.service.PlaceholderService;
import com.dream11.odin.util.AccountUtils;
import com.dream11.odin.util.ApplicationUtil;
import com.dream11.odin.util.ComponentUtil;
import com.dream11.odin.util.JsonUtil;
import com.dream11.odin.util.ServiceUtil;
import com.dream11.odin.util.SingleUtil;
import com.dream11.odin.validations.ServiceStatusValidatorForOperate;
import com.dream11.odin.validations.Validator;
import com.dream11.queue.producer.MessageProducer;
import com.google.inject.Inject;
import com.google.protobuf.util.JsonFormat;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.functions.Function;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.sqlclient.SqlConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public abstract class Operation {

  final ServiceTaskDao serviceTaskDao;
  final ComponentTaskDao componentTaskDao;
  final MysqlClient mysqlClient;
  final DatabasePollerService databasePollerService;
  final MessageProducer<String> messageProducer;
  final PlaceholderService placeholderService;
  final ComponentEnrichmentService componentEnrichmentService;
  final InterceptorService interceptorService;

  abstract Validator inputValidator(
      OperateServiceRequest request,
      ServiceTaskDao serviceTaskDao,
      ComponentTaskDao componentTaskDao,
      Long envId);

  abstract ComponentAction getOperateComponentAction(
      OperateServiceRequest request, List<ComponentTaskEntity> previousComponentTaskEntities);

  private Maybe<List<ComponentTaskEntity>> createTasks(
      OperateServiceRequest request,
      Long envId,
      ComponentAction newComponentAction,
      List<ComponentTaskEntity> oldComponentTaskEntities) {
    return mysqlClient
        .getMasterClient()
        .rxWithTransaction(
            (Function<SqlConnection, Maybe<List<ComponentTaskEntity>>>)
                connection ->
                    serviceTaskDao
                        .createServiceTaskEntityForOperate(
                            request.getServiceName(), envId, Action.OPERATE, request)
                        .flatMap(
                            serviceTaskEntity -> {
                              ServiceTaskEntity updatedServiceTaskEntity =
                                  ServiceTaskEntity.builder()
                                      .version(serviceTaskEntity.getVersion() + 1)
                                      .updatedBy(serviceTaskEntity.getUpdatedBy())
                                      .createdBy(serviceTaskEntity.getCreatedBy())
                                      .status(serviceTaskEntity.getStatus())
                                      .config(serviceTaskEntity.getConfig())
                                      .actions(serviceTaskEntity.getActions())
                                      .name(serviceTaskEntity.getName())
                                      .envId(serviceTaskEntity.getEnvId())
                                      .serviceConfigHash(serviceTaskEntity.getServiceConfigHash())
                                      .serviceVersion(serviceTaskEntity.getServiceVersion())
                                      .traceId(serviceTaskEntity.getTraceId())
                                      .build();
                              return serviceTaskDao
                                  .createServiceTask(connection, updatedServiceTaskEntity)
                                  .flatMap(
                                      createdServiceTaskEntity -> {
                                        ComponentTaskEntity newComponentTaskEntity =
                                            ComponentUtil.getComponentTaskEntity(
                                                newComponentAction, createdServiceTaskEntity);
                                        List<ComponentTaskEntity> newComponentTaskEntities =
                                            new ArrayList<>(
                                                ComponentUtil.filterComponentTaskEntitiesForOperate(
                                                    oldComponentTaskEntities,
                                                    newComponentAction.getComponentName(),
                                                    createdServiceTaskEntity));
                                        newComponentTaskEntities.add(newComponentTaskEntity);
                                        return componentTaskDao.createComponentTasks(
                                            connection, newComponentTaskEntities);
                                      });
                            })
                        .toMaybe());
  }

  public Single<ComponentData> validateAndApplyPlugins(
      @NonNull OperateServiceRequest request, Environment environment, UserDetails userDetails) {
    Validator validator = new Validator();
    validator.add(
        new ServiceStatusValidatorForOperate(
            serviceTaskDao, request.getServiceName(), environment.getId()));
    validator.add(inputValidator(request, serviceTaskDao, componentTaskDao, environment.getId()));

    // Get component name
    String componentName = getComponentName(request);

    // No need to apply plugins in case of remove component operation
    if (request.getOperation().equalsIgnoreCase(ServiceOperations.REMOVE_COMPONENT.name())) {
      return validator
          .validateAll()
          .andThen(
              Single.just(
                  ComponentData.builder()
                      .operationConfig(request.getConfig())
                      .componentDefinition(
                          ComponentDefinition.newBuilder().setName(componentName).build())
                      .build()));
    }

    RequestMetaContext.RequestMetaContextBuilder requestMetaContextBuilder =
        RequestMetaContext.builder()
            .serviceName(request.getServiceName())
            .environment(environment)
            .userDetails(userDetails);

    // For add component operation, get account data from env task
    ComponentData.ComponentDataBuilder componentDataBuilder = ComponentData.builder();
    if (request.getOperation().equalsIgnoreCase(ServiceOperations.ADD_COMPONENT.name())) {
      AddComponentRequestOptions addComponentRequest =
          JsonUtil.jsonToProtoBuilder(
                  JsonUtil.getJsonFromProto(request.getConfig()),
                  AddComponentRequestOptions.newBuilder())
              .build();
      componentDataBuilder
          .environmentProviderAccounts(
              AccountUtils.filterAccount(
                  environment.getAccountInformationList(),
                  addComponentRequest.getProvisioningConfig(0).getDeploymentType()))
          .componentDefinition(addComponentRequest.getComponentDefinition(0))
          .componentProvisioningConfig(addComponentRequest.getProvisioningConfig(0));

      requestMetaContextBuilder.additionalContext(
          Map.of(
              Constants.ACTION,
              Constants.DEPLOY_ACTION,
              Constants.OPERATION,
              request.getOperation()));
    } else {
      componentDataBuilder.componentDefinition(
          // Complete component definition is not required in component operation
          ComponentDefinition.newBuilder().setName(componentName).build());
      requestMetaContextBuilder.additionalContext(
          Map.of(
              Constants.ACTION,
              Constants.OPERATE_ACTION,
              Constants.OPERATION,
              request.getOperation()));
    }

    final RequestMetaContext requestMetaContext = requestMetaContextBuilder.build();

    final ComponentIdentifier componentIdentifier =
        ComponentIdentifier.builder().componentName(componentName).action(Action.OPERATE).build();

    Map<ComponentIdentifier, ComponentData> componentDataMap =
        Map.of(
            componentIdentifier, componentDataBuilder.operationConfig(request.getConfig()).build());

    return validator
        .validateAll()
        .andThen(
            // First enrich component data from database if needed
            componentEnrichmentService
                .enrichComponentsFromDatabase(componentDataMap, requestMetaContext)
                .flatMap(
                    enrichedComponentDataMap ->
                        // Then apply interceptors
                        interceptorService
                            .invokeInterceptors(enrichedComponentDataMap, requestMetaContext)
                            .flatMap(
                                interceptedComponentDataMap ->
                                    // Finally apply placeholders
                                    placeholderService.replacePlaceholdersInComponents(
                                        interceptedComponentDataMap, requestMetaContext)))
                .map(
                    updatedComponentDataMap -> {
                      log.info(
                          "Updated the componentMap using interceptors and placeholders for env {}, proceeding",
                          environment.getName());
                      return updatedComponentDataMap.get(componentIdentifier);
                    }));
  }

  private Flowable<OperateServiceResponse> getResponseFromDBPoller(Long id, String component) {
    return databasePollerService
        .pollDatabase(id, Action.OPERATE, Set.of(component))
        .flatMap(
            serviceResponse -> {
              OperateServiceResponse.Builder operateServiceResponseBuilder =
                  OperateServiceResponse.newBuilder().setServiceResponse(serviceResponse);
              return Flowable.just(operateServiceResponseBuilder.build());
            });
  }

  public Flowable<OperateServiceResponse> operateComponent(
      OperateServiceRequest request, Long envId, Long orgId, ComponentData componentData) {

    Validator validator = new Validator();
    validator.add(
        new ServiceStatusValidatorForOperate(serviceTaskDao, request.getServiceName(), envId));
    return validator
        .validateAll()
        .andThen(
            componentTaskDao
                .getComponentTaskEntities(request.getServiceName(), envId)
                .flatMapPublisher(
                    oldComponentTaskEntities -> {
                      ComponentAction newComponentAction =
                          getOperateComponentAction(request, oldComponentTaskEntities);
                      if (componentData.getEnvironmentProviderAccounts() != null) {
                        newComponentAction.setAccounts(
                            JsonUtil.getMapFromProto(
                                componentData
                                    .getEnvironmentProviderAccounts()
                                    .getServiceAccountsSnapshot()));
                        newComponentAction.setProvider(
                            componentData
                                .getEnvironmentProviderAccounts()
                                .getServiceAccountsSnapshot()
                                .getAccount()
                                .getProvider());
                      }

                      // Process component action
                      Map<ComponentIdentifier, ComponentData> componentDataMap =
                          Map.of(
                              ComponentIdentifier.builder()
                                  .componentName(newComponentAction.getComponentName())
                                  .action(Action.OPERATE)
                                  .build(),
                              componentData);

                      return serviceTaskDao
                          .getServiceTaskByTraceIdServiceNameEnvNameAndAction(
                              ApplicationContext.getTraceId(),
                              request.getServiceName(),
                              request.getEnvName())
                          .flatMapPublisher(
                              serviceTaskEntity -> {
                                log.warn(
                                    "Found service task({}) for traceId, resuming from poller. Potential issue",
                                    serviceTaskEntity.getId());
                                return getResponseFromDBPoller(
                                    serviceTaskEntity.getId(),
                                    newComponentAction.getComponentName());
                              })
                          .switchIfEmpty(
                              Single.just(Pair.of(componentDataMap, newComponentAction))
                                  .flatMapPublisher(
                                      finalComponentAction ->
                                          createTasks(
                                                  request,
                                                  envId,
                                                  finalComponentAction.getRight(),
                                                  oldComponentTaskEntities)
                                              .flatMapPublisher(
                                                  newComponentTaskEntities -> {
                                                    ServiceRequestQueueMessage
                                                        serviceRequestQueueMessage =
                                                            ServiceUtil.createPayload(
                                                                request.getServiceName(),
                                                                request.getEnvName(),
                                                                List.of(
                                                                    finalComponentAction
                                                                        .getRight()),
                                                                newComponentTaskEntities
                                                                    .get(0)
                                                                    .getServiceTaskEntity()
                                                                    .getId(),
                                                                orgId);
                                                    // Add OPERATE message to queue
                                                    return SingleUtil.toSingle(
                                                            messageProducer.send(
                                                                serviceRequestQueueMessage
                                                                    .compressMessage()))
                                                        .flatMapPublisher(
                                                            s ->
                                                                databasePollerService
                                                                    .pollDatabase(
                                                                        newComponentTaskEntities
                                                                            .get(0)
                                                                            .getServiceTaskEntity()
                                                                            .getId(),
                                                                        Action.OPERATE,
                                                                        Set.of(
                                                                            newComponentAction
                                                                                .getComponentName()))
                                                                    .map(
                                                                        serviceResponse ->
                                                                            OperateServiceResponse
                                                                                .newBuilder()
                                                                                .setServiceResponse(
                                                                                    serviceResponse)
                                                                                .build()));
                                                  })));
                    }));
  }

  @SneakyThrows
  public OperateServiceRequest getAddComponentOperationUpdatedConfig(
      OperateServiceRequest request, ComponentData componentData) {
    AddComponentRequestOptions addComponentRequest =
        JsonUtil.jsonToProtoBuilder(
                JsonUtil.getJsonFromProto(request.getConfig()),
                AddComponentRequestOptions.newBuilder())
            .build();
    addComponentRequest =
        addComponentRequest.toBuilder()
            .setComponentDefinition(0, componentData.getComponentDefinition())
            .setProvisioningConfig(0, componentData.getComponentProvisioningConfig())
            .build();

    String addComponentRequestJsonString = JsonFormat.printer().print(addComponentRequest);

    return request.toBuilder()
        .setConfig(ApplicationUtil.toGrpcStruct(new JsonObject(addComponentRequestJsonString)))
        .build();
  }

  public Flowable<ComponentData> validateAndOperate(
      OperateServiceRequest request, Environment environment, UserDetails userDetails) {
    return this.validateAndApplyPlugins(request, environment, userDetails).toFlowable();
  }

  public String getComponentName(OperateServiceRequest request) {
    if (request.getIsComponentOperation()) {
      return request.getComponentName();
    } else {
      if (request.getOperation().equalsIgnoreCase(ServiceOperations.ADD_COMPONENT.name())) {
        AddComponentRequestOptions addComponentRequest =
            JsonUtil.jsonToProtoBuilder(
                    JsonUtil.getJsonFromProto(request.getConfig()),
                    AddComponentRequestOptions.newBuilder())
                .build();
        return addComponentRequest.getComponentDefinition(0).getName();
      } else if (request
          .getOperation()
          .equalsIgnoreCase(ServiceOperations.REMOVE_COMPONENT.name())) {
        RemoveComponentRequestOptions removeComponentRequest =
            JsonUtil.jsonToProtoBuilder(
                    JsonUtil.getJsonFromProto(request.getConfig()),
                    RemoveComponentRequestOptions.newBuilder())
                .build();
        return removeComponentRequest.getComponentName();
      } else {
        throw ExceptionUtil.getException(
            OdinError.INVALID_SERVICE_OPERATION, request.getOperation());
      }
    }
  }
}
