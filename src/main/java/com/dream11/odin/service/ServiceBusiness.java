package com.dream11.odin.service;

import com.dream11.grpc.error.GrpcException;
import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.ApplicationContext;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.constant.ExecTaskType;
import com.dream11.odin.constant.ServiceOperations;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dao.ComponentTaskDao;
import com.dream11.odin.dao.EnvironmentDao;
import com.dream11.odin.dao.ExecutionTaskDao;
import com.dream11.odin.dao.LockDao;
import com.dream11.odin.dao.ServiceComponentDao;
import com.dream11.odin.dao.ServiceTaskDao;
import com.dream11.odin.dao.TransactionDao;
import com.dream11.odin.dto.ComponentData;
import com.dream11.odin.dto.ComponentIdentifier;
import com.dream11.odin.dto.RequestMetaContext;
import com.dream11.odin.dto.ServiceData;
import com.dream11.odin.dto.UserDetails;
import com.dream11.odin.dto.requestqueue.ComponentAction;
import com.dream11.odin.dto.requestqueue.ServiceRequestQueueMessage;
import com.dream11.odin.dto.response.OperateResponse;
import com.dream11.odin.dto.response.StatusResponse;
import com.dream11.odin.dto.v1.AccountInformation;
import com.dream11.odin.dto.v1.ComponentDefinition;
import com.dream11.odin.dto.v1.ComponentProvisioningConfig;
import com.dream11.odin.dto.v1.Environment;
import com.dream11.odin.dto.v1.ProvisioningConfig;
import com.dream11.odin.dto.v1.ServiceDefinition;
import com.dream11.odin.entity.ComponentEntity;
import com.dream11.odin.entity.ComponentTaskEntity;
import com.dream11.odin.entity.EnvironmentEntity;
import com.dream11.odin.entity.EnvironmentEntityWithEnvironmentAccounts;
import com.dream11.odin.entity.EnvironmentServiceEntity;
import com.dream11.odin.entity.EnvironmentServiceEntityWithComponents;
import com.dream11.odin.entity.ServiceTaskEntity;
import com.dream11.odin.error.OdinError;
import com.dream11.odin.error.OdinRestError;
import com.dream11.odin.grpc.service.DeployServiceRequest;
import com.dream11.odin.grpc.service.DeployServiceResponse;
import com.dream11.odin.grpc.service.OperateServiceRequest;
import com.dream11.odin.grpc.service.OperateServiceResponse;
import com.dream11.odin.grpc.service.ServiceResponse;
import com.dream11.odin.grpc.service.ServiceStatus;
import com.dream11.odin.grpc.service.UndeployServiceResponse;
import com.dream11.odin.injector.GuiceInjector;
import com.dream11.odin.service.messagestrategy.MessageBuilderPojo;
import com.dream11.odin.service.messagestrategy.MessageBuilderStrategyFactory;
import com.dream11.odin.service.operation.AddComponentServiceOperation;
import com.dream11.odin.service.operation.ComponentOperation;
import com.dream11.odin.service.operation.Operation;
import com.dream11.odin.service.operation.RemoveComponentServiceOperation;
import com.dream11.odin.util.AccountUtils;
import com.dream11.odin.util.ComponentUtil;
import com.dream11.odin.util.ErrorMapperUtil;
import com.dream11.odin.util.JsonUtil;
import com.dream11.odin.util.ServiceUtil;
import com.dream11.odin.util.SingleUtil;
import com.dream11.odin.util.ValidationUtil;
import com.dream11.odin.validations.EnvironmentRunningValidator;
import com.dream11.odin.validations.ServiceStatusValidatorForUndeploy;
import com.dream11.odin.validations.Validator;
import com.dream11.queue.producer.MessageProducer;
import com.dream11.rest.exception.RestException;
import com.dream11.rest.exception.impl.RestErrorEnum;
import com.google.inject.Inject;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.rpc.Code;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.sqlclient.SqlConnection;
import jakarta.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ServiceBusiness {
  public static final String DEFAULT = "<default>";
  public static final String ERROR_FIELD = "error";
  public static final String DEPENDENT_COMPONENT_FAILURE_ERROR =
      "Component execution failed due to dependent component failure";

  final ExecutionTaskDao executionTaskDao;
  final ServiceComponentDao serviceComponentDao;

  final ComponentTaskDao componentTaskDao;
  final DatabasePollerService databasePollerService;
  final EnvironmentDao environmentDao;
  final MessageProducer<String> messageProducer;
  final ServiceTaskDao serviceTaskDao;
  final LockDao lockDao;
  final PlaceholderService placeholderService;
  final InterceptorService interceptorService;

  final TransactionDao transactionDao;

  Map<String, Action> getAllComponentActions(ServiceData serviceData, Action action) {
    return serviceData.getServiceDefinition().getComponentsList().stream()
        .collect(Collectors.toMap(ComponentDefinition::getName, __ -> action));
  }

  Map<ComponentIdentifier, ComponentData> getComponentsDataMap(
      ServiceData serviceData,
      List<AccountInformation> accountInformationList,
      Map<String, Action> componentActionMap) {
    return serviceData.getServiceDefinition().getComponentsList().stream()
        .collect(
            Collectors.toMap(
                componentDefinition ->
                    ComponentUtil.buildComponentId(
                        componentDefinition.getName(),
                        componentActionMap.get(componentDefinition.getName())),
                componentDefinition -> {
                  ComponentProvisioningConfig componentProvisioningConfig =
                      ComponentUtil.getComponentProvisioningConfig(
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

  private Flowable<DeployServiceResponse> deployService(
      ServiceDefinition serviceDefinition,
      ProvisioningConfig provisioningConfig,
      EnvironmentEntityWithEnvironmentAccounts envWithAccounts,
      UserDetails userDetails,
      String executionId) {
    ServiceData serviceData = this.buildServiceData(serviceDefinition, provisioningConfig);
    Map<String, Action> deployActions = this.getAllComponentActions(serviceData, Action.DEPLOY);
    List<AccountInformation> accountInformationList =
        envWithAccounts.getEnvironmentAccounts().stream()
            .map(AccountUtils::getAccountInformation)
            .toList();
    Map<ComponentIdentifier, ComponentData> componentData =
        this.getComponentsDataMap(serviceData, accountInformationList, deployActions);
    return this.validateAndFilterComponents(
            serviceData, envWithAccounts, userDetails, provisioningConfig, componentData)
        .flatMapPublisher(
            updatedData ->
                this.orchestrateAndPoll(
                    serviceData,
                    envWithAccounts.getEnvironment(),
                    userDetails,
                    updatedData,
                    executionId))
        .map(this::toDeployServiceResponse);
  }

  private ServiceData buildServiceData(ServiceDefinition def, ProvisioningConfig config) {
    return ServiceData.builder()
        .serviceDefinition(def)
        .componentProvisioningConfigs(config.getComponentProvisioningConfigList())
        .build();
  }

  private Single<Map<ComponentIdentifier, ComponentData>> validateAndFilterComponents(
      ServiceData serviceData,
      EnvironmentEntityWithEnvironmentAccounts envWithAccounts,
      UserDetails userDetails,
      ProvisioningConfig provisioningConfig,
      Map<ComponentIdentifier, ComponentData> componentData) {
    return ValidationUtil.validateService(serviceData)
        .andThen(
            ValidationUtil.validateEnvState(
                environmentDao, envWithAccounts.getEnvironment().name(), userDetails))
        .andThen(this.validateDeploymentType(provisioningConfig, envWithAccounts))
        .andThen(
            this.replacePlaceholders(
                componentData, serviceData, envWithAccounts.getEnvironment(), userDetails))
        .flatMap(
            data ->
                ValidationUtil.validateServiceState(
                        serviceComponentDao,
                        envWithAccounts.getEnvironment().id(),
                        data,
                        serviceData)
                    .andThen(
                        this.removeSuccessfulComponents(
                            envWithAccounts.getEnvironment().id(), serviceData, data)));
  }

  private Completable validateDeploymentType(
      ProvisioningConfig provisioningConfig,
      EnvironmentEntityWithEnvironmentAccounts envWithAccounts) {
    return ValidationUtil.validateDeploymentTypePrefix(
        provisioningConfig,
        envWithAccounts.getEnvironmentAccounts().stream()
            .map(AccountUtils::getAccountInformation)
            .toList());
  }

  private Single<Map<ComponentIdentifier, ComponentData>> replacePlaceholders(
      Map<ComponentIdentifier, ComponentData> componentData,
      ServiceData serviceData,
      EnvironmentEntity environment,
      UserDetails userDetails) {

    return placeholderService.replacePlaceholdersInComponents(
        componentData,
        RequestMetaContext.builder()
            .serviceName(serviceData.getServiceDefinition().getName())
            .environment(environment)
            .userDetails(userDetails)
            .additionalContext(Map.of(Constants.ACTION, Constants.DEPLOY_ACTION))
            .build());
  }

  private Flowable<ServiceResponse> orchestrateAndPoll(
      ServiceData serviceData,
      EnvironmentEntity env,
      UserDetails userDetails,
      Map<ComponentIdentifier, ComponentData> componentData,
      String executionId) {

    String serviceName = serviceData.getServiceDefinition().getName();
    Long orgId = userDetails.getOrgId();

    Completable validateAction =
        this.orchestrateServiceAction(
            serviceName,
            env.name(),
            orgId,
            env.id(),
            this.buildEnvironmentServiceEntityWithComponents(
                env.id(), userDetails, Action.VALIDATE, serviceData, componentData),
            executionId,
            componentData,
            Action.VALIDATE,
            true);

    Completable deployAction =
        this.orchestrateServiceAction(
            serviceName,
            env.name(),
            orgId,
            env.id(),
            this.buildEnvironmentServiceEntityWithComponents(
                env.id(), userDetails, Action.DEPLOY, serviceData, componentData),
            executionId,
            componentData,
            Action.DEPLOY,
            false);

    return validateAction
        .andThen(this.databasePollerService.pollDatabase(env.id(), serviceName))
        .flatMap(
            serviceResponse -> {
              if (TaskStatus.SUCCESSFUL
                  .getValue()
                  .equals(serviceResponse.getServiceStatus().getServiceStatus())) {
                return deployAction.andThen(
                    this.databasePollerService.pollDatabase(env.id(), serviceName));
              }
              return Flowable.just(serviceResponse);
            });
  }

  private DeployServiceResponse toDeployServiceResponse(ServiceResponse serviceResponse) {
    return DeployServiceResponse.newBuilder().setServiceResponse(serviceResponse).build();
  }

  private JsonObject buildComponentConfig(ComponentData componentData) {
    return JsonObject.of(
        "componentConfig",
        JsonUtil.getJsonFromProto(componentData.getComponentDefinition()),
        "provisioningConfig",
        JsonUtil.getJsonFromProto(componentData.getComponentProvisioningConfig()));
  }

  private EnvironmentServiceEntityWithComponents buildEnvironmentServiceEntityWithComponents(
      long envId,
      UserDetails userDetails,
      Action action,
      ServiceData serviceData,
      Map<ComponentIdentifier, ComponentData> componentDataMap) {
    return EnvironmentServiceEntityWithComponents.builder()
        .environmentServiceEntity(
            EnvironmentServiceEntity.builder()
                .environmentId(envId)
                .serviceName(serviceData.getServiceDefinition().getName())
                .serviceAction(action)
                .serviceStatus(TaskStatus.IN_PROGRESS)
                .serviceConfig(JsonObject.of("name", serviceData.getServiceDefinition().getName()))
                .createdBy(userDetails.getEmailId())
                .updatedBy(userDetails.getEmailId())
                .build())
        .components(
            componentDataMap.values().stream()
                .<ComponentEntity>map(
                    componentData ->
                        ComponentEntity.builder()
                            .name(componentData.getComponentDefinition().getName())
                            .action(action)
                            .status(TaskStatus.IN_PROGRESS)
                            .config(this.buildComponentConfig(componentData))
                            .accountData(
                                JsonUtil.getJsonFromProto(
                                    componentData
                                        .getEnvironmentProviderAccounts()
                                        .getServiceAccountsSnapshot()))
                            .createdBy(userDetails.getEmailId())
                            .updatedBy(userDetails.getEmailId())
                            .build())
                .toList())
        .build();
  }

  private ServiceRequestQueueMessage buildSqsMessage(
      String envName,
      String serviceName,
      long orgId,
      long serviceId,
      Map<ComponentIdentifier, ComponentData> componentDataMap,
      Action action) {
    return MessageBuilderStrategyFactory.getMessageBuilderStrategy(action)
        .buildMessage(
            MessageBuilderPojo.builder()
                .envName(envName)
                .serviceId(serviceId)
                .serviceName(serviceName)
                .componentDataMap(componentDataMap)
                .orgId(orgId)
                .build());
  }

  private Single<Map<ComponentIdentifier, ComponentData>> removeSuccessfulComponents(
      long envId,
      ServiceData serviceData,
      Map<ComponentIdentifier, ComponentData> componentDataMap) {
    return this.serviceComponentDao
        .getEnvironmentServiceWithComponentsIfExists(
            envId, serviceData.getServiceDefinition().getName())
        .map(
            environmentServiceEntityWithComponents -> {
              environmentServiceEntityWithComponents.getComponents().stream()
                  .filter(entity -> entity.getStatus().equals(TaskStatus.SUCCESSFUL))
                  .forEach(
                      entity ->
                          componentDataMap.remove(
                              ComponentIdentifier.builder()
                                  .componentName(entity.getName())
                                  .action(Action.DEPLOY)
                                  .build()));
              return componentDataMap;
            })
        .switchIfEmpty(Single.just(componentDataMap));
  }

  private Completable orchestrateServiceAction(
      String serviceName,
      String envName,
      long orgId,
      long environmentId,
      EnvironmentServiceEntityWithComponents environmentServiceEntityWithComponents,
      String executionId,
      Map<ComponentIdentifier, ComponentData> componentDataMap,
      Action action,
      boolean isLockRequired) {
    return this.transactionDao
        .executeTransaction(
            sqlConnection ->
                this.serviceComponentDao
                    .upsertEnvironmentService(
                        sqlConnection,
                        environmentId,
                        environmentServiceEntityWithComponents.getEnvironmentServiceEntity())
                    .flatMapCompletable(
                        serviceId ->
                            (isLockRequired
                                    ? this.acquireEnvironmentServiceLocks(
                                        sqlConnection,
                                        environmentId,
                                        serviceId,
                                        environmentServiceEntityWithComponents
                                            .getEnvironmentServiceEntity()
                                            .getCreatedBy())
                                    : Completable.complete())
                                .andThen(
                                    this.serviceComponentDao.upsertEnvironmentServiceComponents(
                                        sqlConnection,
                                        serviceId,
                                        environmentServiceEntityWithComponents.getComponents()))
                                .andThen(
                                    this.createTaskAndPushToQueue(
                                        orgId,
                                        serviceId,
                                        executionId,
                                        envName,
                                        serviceName,
                                        componentDataMap,
                                        action,
                                        environmentServiceEntityWithComponents
                                            .getEnvironmentServiceEntity()
                                            .getCreatedBy())))
                    .toMaybe())
        .ignoreElement();
  }

  private Completable createTaskAndPushToQueue(
      long orgId,
      long serviceId,
      String executionId,
      String environmentName,
      String serviceName,
      Map<ComponentIdentifier, ComponentData> componentDataMap,
      Action action,
      String user) {
    ServiceRequestQueueMessage message =
        this.buildSqsMessage(
            environmentName, serviceName, orgId, serviceId, componentDataMap, action);
    return this.executionTaskDao
        .createExecutionTask(
            action.getName(),
            orgId,
            TaskStatus.IN_PROGRESS.getValue(),
            ExecTaskType.SERVICE.getValue(),
            executionId,
            new JsonObject(message.toJsonString()),
            user)
        .andThen(SingleUtil.toSingle(this.messageProducer.send(message.compressMessage())))
        .ignoreElement();
  }

  private Completable acquireEnvironmentServiceLocks(
      SqlConnection sqlConnection, long environmentId, long serviceId, String user) {
    return lockDao
        .ensureEnvironmentServiceLock(sqlConnection, environmentId, serviceId, user)
        .andThen(lockDao.acquireEnvironmentSharedLock(sqlConnection, environmentId))
        .andThen(
            lockDao.acquireEnvironmentServiceExclusiveLock(
                sqlConnection, environmentId, serviceId));
  }

  public Single<StatusResponse> getServiceTaskStatus(String serviceTaskId) {
    if (!serviceTaskId.matches("\\d+")) {
      return Single.error(new RestException(OdinRestError.OPERATION_ID_INVALID));
    }
    return serviceTaskDao
        .getServiceTaskStatus(serviceTaskId)
        .switchIfEmpty(Single.error(new RestException(OdinRestError.OPERATION_ID_INVALID)))
        .map(status -> StatusResponse.builder().status(status).build())
        .doOnError(error -> log.error("Error in service layer fetching task status", error));
  }

  private Maybe<String> getServiceTaskId(String traceId) {
    return serviceTaskDao.getServiceTaskId(traceId);
  }

  public Single<OperateResponse> operateServiceFromRestEndpoint(
      OperateServiceRequest operateServiceRequest, String traceId) {
    return this.operateService(operateServiceRequest, ApplicationContext.getUserDetails(), false)
        .take(1)
        .singleOrError()
        .flatMapMaybe(response -> this.getServiceTaskId(traceId))
        .map(serviceId -> OperateResponse.builder().operationId(serviceId).build())
        .toSingle()
        .onErrorResumeNext(
            throwable -> {
              if (throwable instanceof GrpcException grpcException) {
                Code grpcCode = grpcException.getGrpcCode();
                Response.Status httpStatus = ErrorMapperUtil.convertGrpcCodeToHttp(grpcCode);
                return Single.error(
                    new RestException(
                        String.valueOf(httpStatus.getStatusCode()),
                        httpStatus.getReasonPhrase(),
                        httpStatus.getStatusCode(),
                        throwable));
              } else {
                return Single.error(new RestException(RestErrorEnum.UNKNOWN_EXCEPTION, throwable));
              }
            });
  }

  public Flowable<OperateServiceResponse> operateService(
      OperateServiceRequest operateServiceRequest, UserDetails userDetails, boolean resumeEnabled) {
    Validator validator = new Validator();
    validator.add(
        new EnvironmentRunningValidator(
            environmentDao, operateServiceRequest.getEnvName(), userDetails));
    return Flowable.just(OperateServiceResponse.newBuilder().build()); // TODO Implement this
    //    return serviceTaskDao
    //        .getServiceTaskByTraceIdServiceNameEnvNameAndAction(
    //            ApplicationContext.getTraceId(),
    //            operateServiceRequest.getServiceName(),
    //            operateServiceRequest.getEnvName())
    //        .flatMapPublisher(
    //            serviceTaskEntity -> {
    //              log.info(
    //                  "Found service task({}) for traceId, resuming from poller",
    //                  serviceTaskEntity.getId());
    //              return getResponseFromDBPoller(
    //                  serviceTaskEntity.getId(), operateServiceRequest.getComponentName());
    //            })
    //        .switchIfEmpty(
    //            validator
    //                .validateAll()
    //                .andThen(
    //                    Flowable.defer(
    //                        () ->
    //                            environmentDao
    //                                .getEnvironmentByNameWithAllFields(
    //                                    userDetails.getOrgId(),
    // operateServiceRequest.getEnvName())
    //                                .flatMapPublisher(
    //                                    environment -> {
    //                                      GuiceInjector injector =
    //                                          SharedDataUtil.getInstance(GuiceInjector.class);
    //                                      if (operateServiceRequest.getIsComponentOperation()) {
    //                                        return processComponentOperation(
    //                                            operateServiceRequest,
    //                                            environment,
    //                                            userDetails,
    //                                            injector,
    //                                            resumeEnabled);
    //                                      } else {
    //                                        if (operateServiceRequest
    //                                            .getOperation()
    //                                            .equalsIgnoreCase(
    //                                                ServiceOperations.ADD_COMPONENT.name())) {
    //                                          return processAddComponentOperation(
    //                                              operateServiceRequest,
    //                                              environment,
    //                                              userDetails,
    //                                              injector);
    //                                        } else if (operateServiceRequest
    //                                            .getOperation()
    //                                            .equalsIgnoreCase(
    //                                                ServiceOperations.REMOVE_COMPONENT.name())) {
    //                                          return processRemoveComponentOperation(
    //                                              operateServiceRequest,
    //                                              environment,
    //                                              userDetails,
    //                                              injector);
    //                                        } else {
    //                                          return Flowable.error(
    //                                              ExceptionUtil.getException(
    //                                                  OdinError.INVALID_SERVICE_OPERATION,
    //                                                  operateServiceRequest.getOperation()));
    //                                        }
    //                                      }
    //                                    }))));
  }

  private Flowable<OperateServiceResponse> processRemoveComponentOperation(
      OperateServiceRequest operateServiceRequest,
      Environment environment,
      UserDetails userDetails,
      GuiceInjector injector) {

    Operation operation = injector.getInstance(RemoveComponentServiceOperation.class);
    return operation
        .validateAndApplyPlugins(operateServiceRequest, environment, userDetails)
        .toFlowable()
        .flatMap(
            updatedComponentData -> {
              // Update operation config with plugin data
              OperateServiceRequest updatedOperateServiceRequest =
                  operateServiceRequest.toBuilder()
                      .setConfig(updatedComponentData.getOperationConfig())
                      .build();

              String componentName = operation.getComponentName(operateServiceRequest);
              return getInProgressServiceTaskId(
                      environment, updatedOperateServiceRequest, componentName, true)
                  .flatMapPublisher(
                      id -> {
                        log.info("Found service task({}) for traceId, resuming from poller", id);
                        return getResponseFromDBPoller(id, componentName);
                      })
                  .switchIfEmpty(
                      operation.operateComponent(
                          updatedOperateServiceRequest,
                          environment.getId(),
                          userDetails.getOrgId(),
                          updatedComponentData));
            });
  }

  private Flowable<OperateServiceResponse> processAddComponentOperation(
      OperateServiceRequest operateServiceRequest,
      Environment environment,
      UserDetails userDetails,
      GuiceInjector injector) {

    Operation operation = injector.getInstance(AddComponentServiceOperation.class);
    return operation
        .validateAndOperate(operateServiceRequest, environment, userDetails)
        .flatMap(
            componentData -> {
              OperateServiceRequest updatedOperateServiceRequest =
                  operation.getAddComponentOperationUpdatedConfig(
                      operateServiceRequest, componentData);

              String componentName = operation.getComponentName(operateServiceRequest);
              return getInProgressServiceTaskId(
                      environment, updatedOperateServiceRequest, componentName, true)
                  .flatMapPublisher(
                      id -> {
                        log.info("Found service task({}) for traceId, resuming from poller", id);
                        return getResponseFromDBPoller(id, componentName);
                      })
                  .switchIfEmpty(
                      operation.operateComponent(
                          updatedOperateServiceRequest,
                          environment.getId(),
                          userDetails.getOrgId(),
                          componentData));
            });
  }

  private Flowable<OperateServiceResponse> processComponentOperation(
      OperateServiceRequest operateServiceRequest,
      Environment environment,
      UserDetails userDetails,
      GuiceInjector injector,
      boolean resumeEnabled) {

    Operation operation = injector.getInstance(ComponentOperation.class);
    return operation
        .validateAndOperate(operateServiceRequest, environment, userDetails)
        .flatMap(
            componentData -> {
              OperateServiceRequest updatedOperateServiceRequest =
                  operateServiceRequest.toBuilder()
                      .setConfig(componentData.getOperationConfig())
                      .build();

              String componentName = operation.getComponentName(operateServiceRequest);
              return getInProgressServiceTaskId(
                      environment, updatedOperateServiceRequest, componentName, resumeEnabled)
                  .flatMapPublisher(
                      id -> {
                        log.info("Found service task({}) for traceId, resuming from poller", id);
                        return getResponseFromDBPoller(id, componentName);
                      })
                  .switchIfEmpty(
                      operation.operateComponent(
                          updatedOperateServiceRequest,
                          environment.getId(),
                          userDetails.getOrgId(),
                          componentData));
            });
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

  private Maybe<Long> getInProgressServiceTaskId(
      Environment environment,
      OperateServiceRequest operateServiceRequest,
      String componentName,
      boolean resumeEnabled) {

    return serviceTaskDao
        .getLatestNonHealthcheckServiceTask(
            environment.getId(), operateServiceRequest.getServiceName())
        .switchIfEmpty(
            Maybe.error(
                ExceptionUtil.getException(
                    OdinError.SERVICE_DOES_NOT_EXIST,
                    operateServiceRequest.getServiceName(),
                    environment.getId())))
        .flatMap(
            serviceTaskEntity ->
                componentTaskDao
                    .getLatestComponentTasks(serviceTaskEntity)
                    .flatMapMaybe(
                        componentTaskEntities -> {
                          if (ServiceUtil.isOperateInProgress(serviceTaskEntity)) {

                            Optional<ComponentTaskEntity> taskEntityOptional =
                                componentTaskEntities.stream()
                                    .filter(
                                        componentTask ->
                                            TaskStatus.IN_PROGRESS.equals(componentTask.getStatus())
                                                && componentName.equals(
                                                    componentTask.getComponentName()))
                                    .findFirst();
                            if (taskEntityOptional.isEmpty()) {
                              return Maybe.empty();
                            }

                            ComponentTaskEntity componentTaskEntity =
                                componentTaskEntities.stream()
                                    .filter(
                                        componentTask ->
                                            componentTask.getComponentName().equals(componentName))
                                    .findFirst()
                                    .orElseThrow(
                                        () ->
                                            new IllegalStateException(
                                                String.format(
                                                    "Component %s not found", componentName)));

                            return resumeOperationIfValid(
                                resumeEnabled,
                                operateServiceRequest,
                                componentTaskEntity,
                                serviceTaskEntity);
                          }
                          return Maybe.empty();
                        }));
  }

  private Maybe<Long> resumeOperationIfValid(
      boolean resumeEnabled,
      OperateServiceRequest operateServiceRequest,
      ComponentTaskEntity componentTaskEntity,
      ServiceTaskEntity serviceTaskEntity) {

    if (!resumeEnabled) {
      return Maybe.error(
          ExceptionUtil.getException(
              OdinError.OPERATION_IN_PROGRESS, operateServiceRequest.getOperation()));
    }

    if ((operateServiceRequest.getIsComponentOperation()
            && isOperateComponentConfigDifferent(operateServiceRequest, componentTaskEntity))
        || (ServiceOperations.ADD_COMPONENT
                .name()
                .equalsIgnoreCase(operateServiceRequest.getOperation())
            && isAddComponentConfigDifferent(operateServiceRequest, componentTaskEntity))) {
      return Maybe.error(
          ExceptionUtil.getException(
              OdinError.OPERATION_IN_PROGRESS_DIFFERENT_CONFIGURATION,
              operateServiceRequest.getOperation()));
    }

    return Maybe.just(serviceTaskEntity.getId());
  }

  private boolean isOperateComponentConfigDifferent(
      OperateServiceRequest operateServiceRequest, ComponentTaskEntity componentTaskEntity) {
    return !JsonUtil.convertProtoToJsonSorted(operateServiceRequest.getConfig())
        .equals(
            JsonUtil.sortJsonObject(
                componentTaskEntity.getConfig().getJsonObject("operationConfig")));
  }

  private boolean isAddComponentConfigDifferent(
      OperateServiceRequest operateServiceRequest, ComponentTaskEntity componentTaskEntity) {

    JsonObject request = JsonUtil.convertProtoToJson(operateServiceRequest.getConfig());

    return !(JsonUtil.sortJsonObject(request.getJsonArray("componentDefinition").getJsonObject(0))
            .equals(
                JsonUtil.sortJsonObject(
                    componentTaskEntity.getConfig().getJsonObject("componentConfig")))
        && JsonUtil.sortJsonObject(request.getJsonArray("provisioningConfig").getJsonObject(0))
            .equals(componentTaskEntity.getConfig().getJsonObject("provisioningConfig")));
  }

  public Completable undeployServiceValidator(long envId, String serviceName) {
    Validator validator = new Validator();
    validator.add(new ServiceStatusValidatorForUndeploy(serviceComponentDao, serviceName, envId));
    return validator.validateAll();
  }

  public Flowable<UndeployServiceResponse> undeployService(
      String envName, String serviceName, UserDetails userDetails, String executionId) {

    return this.environmentDao
        .getEnvironmentWithAccounts(userDetails.getOrgId(), envName)
        .flatMapPublisher(
            envWithAccounts ->
                this.undeployServiceValidator(envWithAccounts.getEnvironment().id(), serviceName)
                    .andThen(
                        this.undeployServiceWithoutValidations(
                            envWithAccounts.getEnvironment(),
                            serviceName,
                            userDetails,
                            executionId)));
  }

  public Flowable<UndeployServiceResponse> undeployServiceWithoutValidations(
      EnvironmentEntity env, String serviceName, UserDetails userDetails, String executionId) {

    // TODO: handle service undeploy with no components
    return this.undeployComponents(env, serviceName, userDetails, executionId)
        .map(
            serviceResponse ->
                UndeployServiceResponse.newBuilder().setServiceResponse(serviceResponse).build());
  }

  private boolean isUndeployInProgressOrSuccessful(ServiceResponse serviceResponse) {
    return Action.UNDEPLOY.getName().equals(serviceResponse.getServiceStatus().getServiceAction())
        && (TaskStatus.IN_PROGRESS
                .getValue()
                .equals(serviceResponse.getServiceStatus().getServiceStatus())
            || TaskStatus.SUCCESSFUL
                .getValue()
                .equals(serviceResponse.getServiceStatus().getServiceStatus()));
  }

  private EnvironmentServiceEntityWithComponents
      filterAndBuildEnvironmentServiceEntityWithComponents(
          EnvironmentServiceEntityWithComponents environmentServiceEntityWithComponents) {

    return environmentServiceEntityWithComponents
        .updateComponents(
            environmentServiceEntityWithComponents.getComponents().stream()
                .filter(
                    componentEntity ->
                        !(componentEntity.getAction().equals(Action.UNDEPLOY)
                            && componentEntity.getStatus().equals(TaskStatus.SUCCESSFUL)))
                .map(
                    componentEntity ->
                        componentEntity
                            .updateAction(Action.UNDEPLOY)
                            .updateStatus(TaskStatus.IN_PROGRESS))
                .toList())
        .updateEnvironmentServiceEntity(
            environmentServiceEntityWithComponents
                .getEnvironmentServiceEntity()
                .updateAction(Action.UNDEPLOY)
                .updateStatus(TaskStatus.IN_PROGRESS));
  }

  Map<ComponentIdentifier, ComponentData> buildComponentDataMap(
      EnvironmentServiceEntityWithComponents environmentServiceEntityWithComponents) {
    return environmentServiceEntityWithComponents.getComponents().stream()
        .collect(
            Collectors.toMap(
                componentEntity ->
                    ComponentUtil.buildComponentId(
                        componentEntity.getName(), componentEntity.getAction()),
                componentEntity ->
                    ComponentData.builder()
                        .componentDefinition(
                            JsonUtil.jsonToProtoBuilder(
                                    componentEntity.getConfig().getJsonObject("componentConfig"),
                                    ComponentDefinition.newBuilder())
                                .build())
                        .componentProvisioningConfig(
                            JsonUtil.jsonToProtoBuilder(
                                    componentEntity.getConfig().getJsonObject("provisioningConfig"),
                                    ComponentProvisioningConfig.newBuilder())
                                .build())
                        .environmentProviderAccounts(
                            JsonUtil.jsonToProtoBuilder(
                                    componentEntity.getAccountData(),
                                    AccountInformation.newBuilder())
                                .build())
                        .build()));
  }

  private Flowable<ServiceResponse> undeployComponents(
      EnvironmentEntity env, String serviceName, UserDetails userDetails, String executionId) {
    return this.databasePollerService
        .pollDatabase(env.id(), serviceName)
        .flatMap(
            serviceResponse ->
                this.isUndeployInProgressOrSuccessful(serviceResponse)
                    ? Flowable.just(serviceResponse)
                    : this.serviceComponentDao
                        .getEnvironmentServiceWithComponents(env.id(), serviceName)
                        .flatMapPublisher(
                            environmentServiceEntityWithComponents -> {
                              EnvironmentServiceEntityWithComponents
                                  updatedEnvironmentServiceEntity =
                                      this.filterAndBuildEnvironmentServiceEntityWithComponents(
                                          environmentServiceEntityWithComponents);
                              return this.orchestrateServiceAction(
                                      serviceName,
                                      env.name(),
                                      userDetails.getOrgId(),
                                      env.id(),
                                      updatedEnvironmentServiceEntity,
                                      executionId,
                                      buildComponentDataMap(updatedEnvironmentServiceEntity),
                                      Action.UNDEPLOY,
                                      true)
                                  .andThen(
                                      this.databasePollerService.pollDatabase(
                                          env.id(), serviceName));
                            }));
  }

  private List<ComponentAction> filterDependentComponentFailureComponents(
      List<ComponentAction> componentActions, List<ComponentTaskEntity> componentTaskEntities) {
    return componentActions.stream()
        .filter(
            componentAction -> !isDependentComponentFailure(componentAction, componentTaskEntities))
        .toList();
  }

  private boolean isDependentComponentFailure(
      ComponentAction componentAction, List<ComponentTaskEntity> componentTaskEntities) {

    JsonObject response =
        componentTaskEntities.stream()
            .filter(
                componentTask ->
                    componentTask.getComponentName().equals(componentAction.getComponentName()))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        String.format(
                            "Component %s not found", componentAction.getComponentName())))
            .getResponse();

    if (response == null) {
      return false;
    }

    return DEPENDENT_COMPONENT_FAILURE_ERROR.equals(response.getString(ERROR_FIELD, ""));
  }

  private List<ComponentAction> filterUndeployedComponentActions(
      List<ComponentAction> componentActions, Set<String> componentsToRemove) {
    return componentActions.stream()
        .filter(componentAction -> !componentsToRemove.contains(componentAction.getComponentName()))
        // Clear dependsOn for all components since undeploy is independent of other components
        .map(
            componentAction -> {
              if (componentAction.getDependsOn() != null
                  && !componentAction.getDependsOn().isEmpty()) {
                return componentAction.withDependsOn(Collections.emptyList());
              }
              return componentAction;
            })
        .toList();
  }

  // TODO why this needs both envName and envID?
  public Flowable<List<UndeployServiceResponse>> undeployAllServicesInEnvWithoutValidations(
      EnvironmentEntity env, UserDetails userDetails) {

    return serviceTaskDao
        .getLatestServiceTasks(env.id())
        .flatMapPublisher(
            serviceNames -> {
              if (serviceNames.isEmpty()) {
                return Flowable.just(
                    List.of(
                        UndeployServiceResponse.newBuilder()
                            .setServiceResponse(
                                ServiceResponse.newBuilder()
                                    .setServiceStatus(
                                        ServiceStatus.newBuilder()
                                            .setServiceAction(Action.UNDEPLOY.getName())
                                            .setServiceStatus(TaskStatus.SUCCESSFUL.getValue())
                                            .build())
                                    .setMessage("No services to undeploy")
                                    .build())
                            .build()));
              }
              return Flowable.combineLatest(
                  serviceNames.stream()
                      .map(
                          serviceName ->
                              this.undeployServiceWithoutValidations(
                                  env, serviceName, userDetails, ""))
                      .toList(),
                  objects ->
                      Arrays.stream(objects).map(UndeployServiceResponse.class::cast).toList());
            });
  }

  //  public Single<OperateComponentDiffResponse> getComponentChanges(
  //      String componentName,
  //      String serviceName,
  //      String envName,
  //      String operationName,
  //      Struct config) {
  //    final UserDetails userDetails = ApplicationContext.getUserDetails();
  //
  //    return environmentDao
  //        .getEnvironmentServiceWithComponent(
  //            userDetails.getOrgId(), envName, serviceName, componentName, true)
  //        .flatMap(
  //            env ->
  //                Flowable.fromIterable(env.getServicesList())
  //                    .filter(service -> service.getName().equals(serviceName))
  //                    .firstOrError()
  //                    .toFlowable()
  //                    .flatMap(service -> Flowable.fromIterable(service.getComponentsList()))
  //                    .filter(component -> component.getName().equals(componentName))
  //                    .firstOrError()
  //                    .map(
  //                        component -> {
  //                          OperateServiceRequest operateServiceRequest =
  //                              OperateServiceRequest.newBuilder()
  //                                  .setEnvName(envName)
  //                                  .setServiceName(serviceName)
  //                                  .setComponentName(componentName)
  //                                  .setIsComponentOperation(true)
  //                                  .setOperation(operationName)
  //                                  .setConfig(config)
  //                                  .build();
  //
  //                          Pair<Struct, Struct> diff =
  //                              compareConfigs(
  //                                  component.getConfig(), operateServiceRequest.getConfig());
  //                          return OperateComponentDiffResponse.newBuilder()
  //                              .setOldValues(diff.getLeft())
  //                              .setNewValues(diff.getRight())
  //                              .build();
  //                        }));
  //  }

  private Pair<Struct, Struct> compareConfigs(Struct oldConfig, Struct newConfig) {
    Struct.Builder oldDiffBuilder = Struct.newBuilder();
    Struct.Builder newDiffBuilder = Struct.newBuilder();

    Map<String, Value> oldFields = oldConfig.getFieldsMap();
    Map<String, Value> newFields = newConfig.getFieldsMap();

    for (Map.Entry<String, Value> entry : newFields.entrySet()) {
      String key = entry.getKey();
      Value newValue = entry.getValue();
      Value oldValue = oldFields.get(key);

      processField(key, newValue, oldValue, oldDiffBuilder, newDiffBuilder);
    }

    return Pair.of(oldDiffBuilder.build(), newDiffBuilder.build());
  }

  private void processField(
      String key,
      Value newValue,
      Value oldValue,
      Struct.Builder oldDiffBuilder,
      Struct.Builder newDiffBuilder) {
    if (oldValue != null) {
      handleExistingOldValue(key, newValue, oldValue, oldDiffBuilder, newDiffBuilder);
    } else {
      handleNullOldValue(key, newValue, oldDiffBuilder, newDiffBuilder);
    }
  }

  private void handleExistingOldValue(
      String key,
      Value newValue,
      Value oldValue,
      Struct.Builder oldDiffBuilder,
      Struct.Builder newDiffBuilder) {
    if (!oldValue.equals(newValue)) {
      if (oldValue.hasStructValue() && newValue.hasStructValue()) {
        Pair<Struct, Struct> nestedDiff =
            compareConfigs(oldValue.getStructValue(), newValue.getStructValue());
        addNestedDiffs(key, nestedDiff, oldDiffBuilder, newDiffBuilder);
      } else {
        oldDiffBuilder.putFields(key, oldValue);
        newDiffBuilder.putFields(key, newValue);
      }
    }
  }

  private void handleNullOldValue(
      String key, Value newValue, Struct.Builder oldDiffBuilder, Struct.Builder newDiffBuilder) {
    if (newValue.hasStructValue()) {
      oldDiffBuilder.putFields(
          key,
          Value.newBuilder()
              .setStructValue(createDefaultStruct(newValue.getStructValue()))
              .build());
    } else {
      oldDiffBuilder.putFields(key, Value.newBuilder().setStringValue(DEFAULT).build());
    }
    newDiffBuilder.putFields(key, newValue);
  }

  private void addNestedDiffs(
      String key,
      Pair<Struct, Struct> nestedDiff,
      Struct.Builder oldDiffBuilder,
      Struct.Builder newDiffBuilder) {
    if (!nestedDiff.getLeft().getFieldsMap().isEmpty()) {
      oldDiffBuilder.putFields(
          key, Value.newBuilder().setStructValue(nestedDiff.getLeft()).build());
    }
    if (!nestedDiff.getRight().getFieldsMap().isEmpty()) {
      newDiffBuilder.putFields(
          key, Value.newBuilder().setStructValue(nestedDiff.getRight()).build());
    }
  }

  private Struct createDefaultStruct(Struct struct) {
    Struct.Builder defaultStructBuilder = Struct.newBuilder();
    for (Map.Entry<String, Value> entry : struct.getFieldsMap().entrySet()) {
      String key = entry.getKey();
      Value value = entry.getValue();
      if (value.hasStructValue()) {
        defaultStructBuilder.putFields(
            key,
            Value.newBuilder().setStructValue(createDefaultStruct(value.getStructValue())).build());
      } else {
        defaultStructBuilder.putFields(key, Value.newBuilder().setStringValue(DEFAULT).build());
      }
    }
    return defaultStructBuilder.build();
  }
  //
  //  public Flowable<StatusEnvironmentResponse> getAllServiceStatus(
  //      Environment environment, UserDetails userDetails, String serviceName) {
  //    List<ServiceTask> filteredServiceTasks;
  //    if (!Objects.isNull(serviceName) && !serviceName.isBlank()) {
  //      filteredServiceTasks =
  //          environment.getServicesList().stream()
  //              .filter(serviceTask -> serviceName.equalsIgnoreCase(serviceTask.getName()))
  //              .toList();
  //    } else {
  //      filteredServiceTasks = environment.getServicesList();
  //    }
  //    if (!filteredServiceTasks.isEmpty()) {
  //      StatusEnvironmentResponse.Builder servicesStatus = StatusEnvironmentResponse.newBuilder();
  //      servicesStatus.setEnvName(environment.getName()).setEnvStatus(environment.getStatus());
  //
  //      return Flowable.fromIterable(filteredServiceTasks)
  //          .flatMap(
  //              serviceTaskEntity ->
  //                  getServiceStatus(environment, serviceTaskEntity, userDetails)
  //                      .map(
  //                          serviceComponentStatus ->
  //                              StatusEnvironmentResponse.newBuilder()
  //                                  .setEnvName(environment.getName())
  //                                  .setEnvStatus(environment.getStatus())
  //                                  .addServicesStatus(serviceComponentStatus)
  //                                  .build()))
  //          .doOnError(
  //              throwable ->
  //                  log.error(
  //                      "Error while getting  status of services in environment: {}",
  //                      environment.getName(),
  //                      throwable));
  //    } else if (serviceName != null && serviceName.isBlank()) {
  //      return Flowable.just(
  //          StatusEnvironmentResponse.newBuilder()
  //              .setEnvName(environment.getName())
  //              .setEnvStatus(environment.getStatus())
  //              .build());
  //    }
  //    throw ExceptionUtil.getException(
  //        OdinError.SERVICE_DOES_NOT_EXIST_IN_ENV, serviceName, environment.getName());
  //  }

  @SneakyThrows
  public Flowable<DeployServiceResponse> deployService(
      DeployServiceRequest request, UserDetails userDetails, String executionId) {
    return this.environmentDao
        .getEnvironmentWithAccounts(userDetails.getOrgId(), request.getEnvName())
        .flatMapPublisher(
            envWithAccounts ->
                this.executionTaskDao
                    .getExecutionByIdAndAction(executionId, Action.DEPLOY.getName())
                    .flatMapPublisher(
                        executionTaskEntityList ->
                            this.databasePollerService
                                .pollDatabase(
                                    envWithAccounts.getEnvironment().id(),
                                    request.getServiceDefinition().getName())
                                .map(
                                    serviceResponse -> {
                                      log.info(
                                          "Found response for executionId: {}, serviceName: {}, envName: {}",
                                          executionId,
                                          request.getServiceDefinition().getName(),
                                          request.getEnvName());
                                      return DeployServiceResponse.newBuilder()
                                          .setServiceResponse(serviceResponse)
                                          .build();
                                    }))
                    .switchIfEmpty(
                        this.deployService(
                                request.getServiceDefinition(),
                                request.getProvisioningConfig(),
                                envWithAccounts,
                                userDetails,
                                executionId)
                            .map(
                                response -> {
                                  if (response.hasServiceResponse()) {
                                    return response.toBuilder()
                                        .setServiceResponse(
                                            response.getServiceResponse().toBuilder()
                                                .setName(request.getServiceDefinition().getName())
                                                .build())
                                        .build();
                                  }
                                  return response;
                                })));
  }
}
