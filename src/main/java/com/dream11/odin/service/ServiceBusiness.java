package com.dream11.odin.service;

import static com.dream11.odin.constant.Constants.COMPONENT_CONFIG_KEY;
import static com.dream11.odin.constant.Constants.PROVISIONING_CONFIG_KEY;

import com.dream11.grpc.error.GrpcException;
import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.ApplicationContext;
import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.constant.ExecTaskType;
import com.dream11.odin.constant.ServiceOperations;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dao.ComponentTaskDao;
import com.dream11.odin.dao.ComponentValidateTaskDao;
import com.dream11.odin.dao.EnvironmentDao;
import com.dream11.odin.dao.ExecutionTaskDao;
import com.dream11.odin.dao.LockDao;
import com.dream11.odin.dao.ServiceComponentDao;
import com.dream11.odin.dao.ServiceTaskDao;
import com.dream11.odin.dao.ServiceValidateTaskDao;
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
import com.dream11.odin.dto.v1.ServiceTask;
import com.dream11.odin.entity.ComponentEntity;
import com.dream11.odin.entity.ComponentTaskEntity;
import com.dream11.odin.entity.ComponentValidateTaskEntity;
import com.dream11.odin.entity.EnvironmentServiceEntity;
import com.dream11.odin.entity.EnvironmentServiceEntityWithComponents;
import com.dream11.odin.entity.ServiceTaskEntity;
import com.dream11.odin.error.OdinError;
import com.dream11.odin.error.OdinRestError;
import com.dream11.odin.grpc.environment.DeployedServiceStatus;
import com.dream11.odin.grpc.environment.StatusEnvComponentStatus;
import com.dream11.odin.grpc.environment.StatusEnvironmentResponse;
import com.dream11.odin.grpc.service.DeployServiceRequest;
import com.dream11.odin.grpc.service.DeployServiceResponse;
import com.dream11.odin.grpc.service.OperateComponentDiffResponse;
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
import com.dream11.odin.util.ActionUtil;
import com.dream11.odin.util.ComponentUtil;
import com.dream11.odin.util.ErrorMapperUtil;
import com.dream11.odin.util.JsonUtil;
import com.dream11.odin.util.ServiceUtil;
import com.dream11.odin.util.SharedDataUtil;
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
import io.reactivex.functions.Function;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.sqlclient.SqlConnection;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  final ComponentValidateTaskDao componentValidateTaskDao;
  final ServiceValidateTaskDao serviceValidateTaskDao;
  final EnvironmentDao environmentDao;
  final MessageProducer<String> messageProducer;
  final MysqlClient mysqlClient;
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
      String envName,
      UserDetails userDetails,
      String executionId) {
    ServiceData serviceData = buildServiceData(serviceDefinition, provisioningConfig);
    return fetchEnvironment(userDetails.getOrgId(), envName)
        .flatMapPublisher(
            env -> {
              Map<String, Action> deployActions =
                  getAllComponentActions(serviceData, Action.DEPLOY);
              Map<ComponentIdentifier, ComponentData> componentData =
                  getComponentsDataMap(serviceData, env.getAccountInformationList(), deployActions);
              return validateAndFilterComponents(
                      serviceData, env, userDetails, provisioningConfig, componentData)
                  .flatMapPublisher(
                      updatedData ->
                          orchestrateAndPoll(
                              serviceData, env, userDetails, updatedData, executionId))
                  .map(this::toDeployServiceResponse);
            });
  }

  private ServiceData buildServiceData(ServiceDefinition def, ProvisioningConfig config) {
    return ServiceData.builder()
        .serviceDefinition(def)
        .componentProvisioningConfigs(config.getComponentProvisioningConfigList())
        .build();
  }

  Single<Environment> fetchEnvironment(Long orgId, String envName) {
    return environmentDao.getEnvironmentByNameWithAllFields(orgId, envName);
  }

  private Single<Map<ComponentIdentifier, ComponentData>> validateAndFilterComponents(
      ServiceData serviceData,
      Environment environment,
      UserDetails userDetails,
      ProvisioningConfig provisioningConfig,
      Map<ComponentIdentifier, ComponentData> componentData) {
    return ValidationUtil.validateService(serviceData)
        .andThen(
            ValidationUtil.validateEnvState(environmentDao, environment.getName(), userDetails))
        .andThen(validateDeploymentType(provisioningConfig, environment, environment.getName()))
        .andThen(replacePlaceholders(componentData, serviceData, environment, userDetails))
        .flatMap(
            data ->
                ValidationUtil.validateServiceState(
                        serviceComponentDao,
                        environment.getName(),
                        data,
                        serviceData,
                        userDetails.getOrgId())
                    .andThen(
                        removeSuccessfulComponents(
                            userDetails.getOrgId(), environment.getName(), serviceData, data)));
  }

  private Completable validateDeploymentType(
      ProvisioningConfig provisioningConfig, Environment environment, String envName) {
    return ValidationUtil.validateDeploymentTypePrefix(
        provisioningConfig, environment.getAccountInformationList(), envName);
  }

  private Single<Map<ComponentIdentifier, ComponentData>> replacePlaceholders(
      Map<ComponentIdentifier, ComponentData> componentData,
      ServiceData serviceData,
      Environment environment,
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
      Environment env,
      UserDetails userDetails,
      Map<ComponentIdentifier, ComponentData> componentData,
      String executionId) {

    String serviceName = serviceData.getServiceDefinition().getName();
    Long orgId = userDetails.getOrgId();

    Completable validateAction =
        orchestrateServiceAction(
            serviceName,
            env.getName(),
            orgId,
            env.getId(),
            buildEnvironmentServiceEntityWithComponents(
                env.getName(), userDetails, Action.VALIDATE, serviceData, componentData),
            executionId,
            componentData,
            Action.VALIDATE,
            true);

    Completable deployAction =
        orchestrateServiceAction(
            serviceName,
            env.getName(),
            orgId,
            env.getId(),
            buildEnvironmentServiceEntityWithComponents(
                env.getName(), userDetails, Action.DEPLOY, serviceData, componentData),
            executionId,
            componentData,
            Action.DEPLOY,
            false);

    return validateAction
        .andThen(databasePollerService.pollDatabase(serviceName, env.getName(), orgId))
        .flatMap(
            serviceResponse -> {
              if (TaskStatus.SUCCESSFUL
                  .getValue()
                  .equals(serviceResponse.getServiceStatus().getServiceStatus())) {
                return deployAction.andThen(
                    databasePollerService.pollDatabase(serviceName, env.getName(), orgId));
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
        componentData.getComponentDefinition(),
        "provisioningConfig",
        componentData.getComponentProvisioningConfig());
  }

  private EnvironmentServiceEntityWithComponents buildEnvironmentServiceEntityWithComponents(
      String envName,
      UserDetails userDetails,
      Action action,
      ServiceData serviceData,
      Map<ComponentIdentifier, ComponentData> componentDataMap) {
    return EnvironmentServiceEntityWithComponents.builder()
        .environmentServiceEntity(
            EnvironmentServiceEntity.builder()
                .environmentName(envName)
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
                    value ->
                        ComponentEntity.builder()
                            .name(value.getComponentDefinition().getName())
                            .action(action)
                            .status(TaskStatus.IN_PROGRESS)
                            .config(buildComponentConfig(value))
                            .accountData(
                                JsonUtil.getJsonFromProto(
                                    value
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
      long orgId,
      String envName,
      ServiceData serviceData,
      Map<ComponentIdentifier, ComponentData> componentDataMap) {
    return serviceComponentDao
        .getServiceComponentStateInEnv(orgId, envName, serviceData.getServiceDefinition().getName())
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
    return transactionDao
        .executeTransaction(
            sqlConnection ->
                serviceComponentDao
                    .upsertEnvironmentService(
                        sqlConnection,
                        environmentId,
                        environmentServiceEntityWithComponents.getEnvironmentServiceEntity())
                    .flatMap(
                        serviceId ->
                            (isLockRequired
                                    ? acquireEnvironmentServiceLocks(
                                        sqlConnection,
                                        environmentId,
                                        serviceId,
                                        environmentServiceEntityWithComponents
                                            .getEnvironmentServiceEntity()
                                            .getCreatedBy())
                                    : Completable.complete())
                                .andThen(
                                    serviceComponentDao.upsertEnvironmentServiceComponents(
                                        sqlConnection,
                                        serviceId,
                                        environmentServiceEntityWithComponents.getComponents()))
                                .andThen(
                                    Single.just(
                                        buildSqsMessage(
                                                envName,
                                                serviceName,
                                                orgId,
                                                serviceId,
                                                componentDataMap,
                                                action)
                                            .compressMessage()))
                                .flatMap(
                                    compressedPayload ->
                                        executionTaskDao
                                            .createExecutionTask(
                                                action.getName(),
                                                orgId,
                                                TaskStatus.IN_PROGRESS.getValue(),
                                                ExecTaskType.SERVICE.getValue(),
                                                executionId,
                                                compressedPayload,
                                                environmentServiceEntityWithComponents
                                                    .getEnvironmentServiceEntity()
                                                    .getCreatedBy())
                                            .andThen(Single.just(compressedPayload)))
                                .flatMap(
                                    compressedPayload ->
                                        SingleUtil.toSingle(
                                            messageProducer.send(compressedPayload))))
                    .toMaybe())
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

  public Flowable<ServiceResponse> applyActionToService(
      ServiceData serviceData,
      Map<ComponentIdentifier, ComponentData> componentDataMap,
      Environment environment,
      List<ComponentAction> componentActions,
      UserDetails userDetails,
      Action action,
      int prevServiceTaskEntityVersion) {
    log.info(
        "Applying action {} for service {} in env {} with prevServiceTaskEntityVersion {}",
        action.getName(),
        serviceData.getServiceDefinition().getName(),
        environment.getName(),
        prevServiceTaskEntityVersion);
    return serviceTaskDao
        .getServiceTaskByTraceIdServiceNameEnvNameAndAction(
            ApplicationContext.getTraceId(),
            serviceData.getServiceDefinition().getName(),
            environment.getName())
        .flatMapPublisher(
            serviceTaskEntity -> {
              log.warn(
                  "Found service task({}) for traceId, resuming from poller. Potential issue",
                  serviceTaskEntity.getId());
              return databasePollerService.pollDatabase(serviceTaskEntity.getId(), Action.DEPLOY);
            })
        .switchIfEmpty(
            Single.just(Pair.of(componentDataMap, componentActions))
                .flatMapPublisher(
                    finalComponentActions ->
                        createServiceAndComponentTasks(
                                serviceData,
                                finalComponentActions.getLeft(),
                                action,
                                finalComponentActions.getRight(),
                                environment,
                                userDetails,
                                prevServiceTaskEntityVersion)
                            .switchIfEmpty(
                                Single.error(new IllegalStateException("Failed to create tasks")))
                            .toFlowable()
                            .flatMap(
                                componentTaskEntities -> {
                                  ServiceRequestQueueMessage serviceRequestQueueMessage =
                                      ServiceUtil.createPayload(
                                          serviceData.getServiceDefinition().getName(),
                                          environment.getName(),
                                          finalComponentActions.getRight(),
                                          componentTaskEntities
                                              .get(0)
                                              .getServiceTaskEntity()
                                              .getId(),
                                          userDetails.getOrgId());
                                  log.info(
                                      "ServiceRequestQueueMessage for deploy : {}",
                                      serviceRequestQueueMessage.toJsonString());
                                  // Add DEPLOY message to queue
                                  return SingleUtil.toSingle(
                                          messageProducer.send(
                                              serviceRequestQueueMessage.compressMessage()))
                                      .toFlowable()
                                      .flatMap(
                                          s ->
                                              databasePollerService.pollDatabase(
                                                  componentTaskEntities
                                                      .get(0)
                                                      .getServiceTaskEntity()
                                                      .getId(),
                                                  action));
                                })));
  }

  protected Maybe<List<ComponentTaskEntity>> createServiceAndComponentTasks(
      ServiceData serviceData,
      Map<ComponentIdentifier, ComponentData> componentDataMap,
      Action serviceAction,
      List<ComponentAction> componentActions,
      Environment environment,
      UserDetails userDetails,
      int prevServiceTaskEntityVersion) {
    return mysqlClient
        .getMasterClient()
        .rxWithTransaction(
            (Function<SqlConnection, Maybe<List<ComponentTaskEntity>>>)
                connection ->
                    serviceTaskDao
                        .createServiceTask(
                            connection,
                            ServiceUtil.createServiceTaskEntity(
                                serviceData,
                                environment,
                                serviceAction,
                                userDetails,
                                prevServiceTaskEntityVersion))
                        .flatMap(
                            serviceTaskEntity ->
                                componentTaskDao.createComponentTasks(
                                    connection,
                                    ComponentUtil.createComponentTaskEntities(
                                        componentDataMap,
                                        serviceTaskEntity,
                                        componentActions,
                                        userDetails)))
                        .toMaybe());
  }

  public Flowable<ServiceResponse> validate(
      ServiceData serviceData,
      Map<ComponentIdentifier, ComponentData> componentDataMap,
      Environment environment,
      UserDetails userDetails) {
    Map<String, Object> validateStageConfig = Map.of("stageName", "deploy");

    // Call interceptors before validate stage
    RequestMetaContext validateContext =
        RequestMetaContext.builder()
            .serviceName(serviceData.getServiceDefinition().getName())
            .environment(environment)
            .userDetails(userDetails)
            .additionalContext(Map.of(Constants.ACTION, Constants.VALIDATE_ACTION))
            .build();

    return interceptorService
        .invokeInterceptors(componentDataMap, validateContext)
        .flatMapPublisher(
            interceptedComponentDataMap -> {
              List<ComponentAction> componentValidateActions =
                  ActionUtil.buildComponentActions(
                      interceptedComponentDataMap, Action.VALIDATE, validateStageConfig);
              // Create validate task and trigger validate
              return createValidateTasks(serviceData, interceptedComponentDataMap, userDetails)
                  .switchIfEmpty(
                      Single.error(new IllegalStateException("Failed to create validate tasks")))
                  .flatMapPublisher(
                      componentValidateTaskEntities -> {
                        ServiceRequestQueueMessage serviceRequestQueueMessage =
                            ServiceUtil.createPayload(
                                serviceData.getServiceDefinition().getName(),
                                Constants.VALIDATE_NAMESPACE,
                                componentValidateActions,
                                componentValidateTaskEntities
                                    .get(0)
                                    .getServiceValidateTaskEntity()
                                    .getId(),
                                userDetails.getOrgId());
                        // Add VALIDATE message to queue
                        return SingleUtil.toSingle(
                                messageProducer.send(serviceRequestQueueMessage.compressMessage()))
                            .toFlowable()
                            .flatMap(
                                s ->
                                    databasePollerService.pollDatabase(
                                        componentValidateTaskEntities
                                            .get(0)
                                            .getServiceValidateTaskEntity()
                                            .getId(),
                                        Action.VALIDATE));
                      });
            });
  }

  public Maybe<List<ComponentValidateTaskEntity>> createValidateTasks(
      ServiceData serviceData,
      Map<ComponentIdentifier, ComponentData> componentDataMap,
      UserDetails userDetails) {
    return mysqlClient
        .getMasterClient()
        .rxWithTransaction(
            (Function<SqlConnection, Maybe<List<ComponentValidateTaskEntity>>>)
                connection ->
                    serviceValidateTaskDao
                        .createServiceValidateTask(
                            connection,
                            ValidationUtil.createServiceValidateTaskEntity(
                                serviceData, userDetails))
                        .flatMap(
                            serviceValidateTaskEntity ->
                                componentValidateTaskDao.createComponentValidateTasks(
                                    connection,
                                    ValidationUtil.createComponentValidateTaskEntities(
                                        serviceData.getServiceDefinition(),
                                        componentDataMap,
                                        serviceValidateTaskEntity,
                                        userDetails)))
                        .toMaybe());
  }

  public Flowable<OperateServiceResponse> operateService(
      OperateServiceRequest operateServiceRequest, UserDetails userDetails, boolean resumeEnabled) {
    Validator validator = new Validator();
    validator.add(
        new EnvironmentRunningValidator(
            environmentDao, operateServiceRequest.getEnvName(), userDetails));
    return serviceTaskDao
        .getServiceTaskByTraceIdServiceNameEnvNameAndAction(
            ApplicationContext.getTraceId(),
            operateServiceRequest.getServiceName(),
            operateServiceRequest.getEnvName())
        .flatMapPublisher(
            serviceTaskEntity -> {
              log.info(
                  "Found service task({}) for traceId, resuming from poller",
                  serviceTaskEntity.getId());
              return getResponseFromDBPoller(
                  serviceTaskEntity.getId(), operateServiceRequest.getComponentName());
            })
        .switchIfEmpty(
            validator
                .validateAll()
                .andThen(
                    Flowable.defer(
                        () ->
                            environmentDao
                                .getEnvironmentByNameWithAllFields(
                                    userDetails.getOrgId(), operateServiceRequest.getEnvName())
                                .flatMapPublisher(
                                    environment -> {
                                      GuiceInjector injector =
                                          SharedDataUtil.getInstance(GuiceInjector.class);
                                      if (operateServiceRequest.getIsComponentOperation()) {
                                        return processComponentOperation(
                                            operateServiceRequest,
                                            environment,
                                            userDetails,
                                            injector,
                                            resumeEnabled);
                                      } else {
                                        if (operateServiceRequest
                                            .getOperation()
                                            .equalsIgnoreCase(
                                                ServiceOperations.ADD_COMPONENT.name())) {
                                          return processAddComponentOperation(
                                              operateServiceRequest,
                                              environment,
                                              userDetails,
                                              injector);
                                        } else if (operateServiceRequest
                                            .getOperation()
                                            .equalsIgnoreCase(
                                                ServiceOperations.REMOVE_COMPONENT.name())) {
                                          return processRemoveComponentOperation(
                                              operateServiceRequest,
                                              environment,
                                              userDetails,
                                              injector);
                                        } else {
                                          return Flowable.error(
                                              ExceptionUtil.getException(
                                                  OdinError.INVALID_SERVICE_OPERATION,
                                                  operateServiceRequest.getOperation()));
                                        }
                                      }
                                    }))));
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
                    OdinError.SERVICE_DOES_NOT_EXIST_IN_ENV,
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

  public Completable undeployServiceValidator(
      String envName, String serviceName, UserDetails userDetails) {

    return environmentDao
        .getEnvironmentByNameWithAllFields(userDetails.getOrgId(), envName)
        .flatMapCompletable(
            environment -> {
              Validator validator = new Validator();
              validator.add(
                  new ServiceStatusValidatorForUndeploy(
                      serviceTaskDao, serviceName, environment.getId()));
              return validator.validateAll();
            });
  }

  public Flowable<UndeployServiceResponse> undeployService(
      String envName, String serviceName, UserDetails userDetails) {

    return undeployServiceValidator(envName, serviceName, userDetails)
        .andThen(undeployServiceWithoutValidations(envName, serviceName, userDetails));
  }

  public Flowable<UndeployServiceResponse> undeployServiceWithoutValidations(
      String envName, String serviceName, UserDetails userDetails) {

    // TODO: handle service undeploy with no components
    return Flowable.defer(
        () ->
            undeployComponents(serviceName, envName, userDetails, new ArrayList<>())
                .map(
                    serviceResponse ->
                        UndeployServiceResponse.newBuilder()
                            .setServiceResponse(serviceResponse)
                            .build()));
  }

  private Flowable<ServiceResponse> undeployComponents(
      String serviceName,
      String environmentName,
      UserDetails userDetails,
      List<String> components) {
    return environmentDao
        .getEnvironmentWithServices(userDetails.getOrgId(), environmentName)
        .flatMapPublisher(
            environment ->
                serviceTaskDao
                    .getLatestNonHealthcheckServiceTask(environment.getId(), serviceName)
                    .flatMapPublisher(
                        serviceTaskEntity ->
                            databasePollerService
                                .pollDatabase(serviceTaskEntity.getId(), Action.UNDEPLOY)
                                .flatMap(
                                    serviceResponse -> {
                                      if (ServiceUtil.isUndeployInProgressOrSuccessful(
                                          serviceResponse)) {
                                        return Flowable.just(serviceResponse);
                                      }

                                      // why are we going again to get service tasks?
                                      return serviceTaskDao
                                          .getLatestCompletedServiceTask(
                                              environment.getId(), serviceName)
                                          .flatMapPublisher(
                                              latestServiceTask ->
                                                  componentTaskDao
                                                      .getLatestComponentTasks(serviceTaskEntity)
                                                      .flatMapPublisher(
                                                          componentTaskEntities ->
                                                              // why are we going again to get
                                                              // environment?
                                                              environmentDao
                                                                  .getEnvironmentByNameWithAllFields(
                                                                      userDetails.getOrgId(),
                                                                      environmentName)
                                                                  .flatMapPublisher(
                                                                      environmentObject ->
                                                                          filterUndeployedComponentsAndApplyAction(
                                                                              environmentObject,
                                                                              componentTaskEntities,
                                                                              userDetails,
                                                                              components,
                                                                              latestServiceTask,
                                                                              Action.UNDEPLOY))));
                                    })));
  }

  private Flowable<ServiceResponse> filterUndeployedComponentsAndApplyAction(
      Environment environment,
      List<ComponentTaskEntity> componentTaskEntities,
      UserDetails userDetails,
      List<String> components,
      ServiceTaskEntity serviceTaskEntity,
      Action action) {
    // Remove already undeployed components
    Set<String> undeployedComponentNames =
        componentTaskEntities.stream()
            .filter(
                componentTaskEntity ->
                    componentTaskEntity.getStatus().equals(TaskStatus.SUCCESSFUL)
                        && componentTaskEntity.getAction().equals(Action.UNDEPLOY))
            .map(ComponentTaskEntity::getComponentName)
            .collect(Collectors.toSet());

    ServiceDefinition serviceDefinitionConfig =
        JsonUtil.jsonToProtoBuilder(serviceTaskEntity.getConfig(), ServiceDefinition.newBuilder())
            .build();

    Set<String> componentNames = new HashSet<>(components);
    List<ComponentDefinition> componentDefinitions =
        componentTaskEntities.stream()
            .filter(
                componentTaskEntity ->
                    componentNames.isEmpty()
                        || !componentNames.contains(componentTaskEntity.getComponentName()))
            .map(
                componentTaskEntity -> {
                  componentNames.add(componentTaskEntity.getComponentName());
                  JsonObject componentDefinitionJson =
                      componentTaskEntity.getConfig().getJsonObject(COMPONENT_CONFIG_KEY);
                  return JsonUtil.jsonToProtoBuilder(
                          componentDefinitionJson, ComponentDefinition.newBuilder())
                      .build();
                })
            .toList();

    List<ComponentProvisioningConfig> componentProvisioningConfigs =
        componentTaskEntities.stream()
            .map(
                componentTaskEntity ->
                    JsonUtil.jsonToProtoBuilder(
                            componentTaskEntity.getConfig().getJsonObject(PROVISIONING_CONFIG_KEY),
                            ComponentProvisioningConfig.newBuilder())
                        .build())
            .toList();

    ServiceDefinition serviceDefinition =
        ServiceDefinition.newBuilder(serviceDefinitionConfig)
            .addAllComponents(componentDefinitions)
            .build();

    ServiceData serviceData =
        ServiceData.builder()
            .serviceDefinition(serviceDefinition)
            .componentProvisioningConfigs(componentProvisioningConfigs)
            .build();

    Map<ComponentIdentifier, ComponentData> componentDataMap =
        ComponentUtil.buildComponentsData(serviceData, action, componentTaskEntities);

    List<ComponentAction> componentActions =
        ActionUtil.buildComponentActions(componentDataMap, action, new HashMap<>());

    Action actionToApply;
    if (action == Action.HEALTHCHECK) {
      actionToApply = Action.HEALTHCHECK;
    } else {
      actionToApply = components.isEmpty() ? Action.UNDEPLOY : Action.OPERATE;
    }

    List<ComponentAction> filteredComponentActions =
        filterUpdeployedComponentActions(componentActions, undeployedComponentNames);

    if (actionToApply == Action.UNDEPLOY) {
      filteredComponentActions =
          filterDependentComponentFailureComponents(
              filteredComponentActions, componentTaskEntities);
    }

    return applyActionToService(
        serviceData,
        componentDataMap,
        environment,
        filteredComponentActions,
        userDetails,
        actionToApply,
        serviceTaskEntity.getVersion());
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

  private List<ComponentAction> filterUpdeployedComponentActions(
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

  public Flowable<List<UndeployServiceResponse>> undeployAllServicesInEnvWithoutValidations(
      String envName, Long envId, UserDetails userDetails) {

    return serviceTaskDao
        .getLatestServiceTasks(envId)
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
                              undeployServiceWithoutValidations(envName, serviceName, userDetails))
                      .toList(),
                  objects ->
                      Arrays.stream(objects).map(UndeployServiceResponse.class::cast).toList());
            });
  }

  public Single<OperateComponentDiffResponse> getComponentChanges(
      String componentName,
      String serviceName,
      String envName,
      String operationName,
      Struct config) {
    final UserDetails userDetails = ApplicationContext.getUserDetails();

    return environmentDao
        .getEnvironmentServiceWithComponent(
            userDetails.getOrgId(), envName, serviceName, componentName, true)
        .flatMap(
            env ->
                Flowable.fromIterable(env.getServicesList())
                    .filter(service -> service.getName().equals(serviceName))
                    .firstOrError()
                    .toFlowable()
                    .flatMap(service -> Flowable.fromIterable(service.getComponentsList()))
                    .filter(component -> component.getName().equals(componentName))
                    .firstOrError()
                    .map(
                        component -> {
                          OperateServiceRequest operateServiceRequest =
                              OperateServiceRequest.newBuilder()
                                  .setEnvName(envName)
                                  .setServiceName(serviceName)
                                  .setComponentName(componentName)
                                  .setIsComponentOperation(true)
                                  .setOperation(operationName)
                                  .setConfig(config)
                                  .build();

                          Pair<Struct, Struct> diff =
                              compareConfigs(
                                  component.getConfig(), operateServiceRequest.getConfig());
                          return OperateComponentDiffResponse.newBuilder()
                              .setOldValues(diff.getLeft())
                              .setNewValues(diff.getRight())
                              .build();
                        }));
  }

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

  public Flowable<StatusEnvironmentResponse> getAllServiceStatus(
      Environment environment, UserDetails userDetails, String serviceName) {
    List<ServiceTask> filteredServiceTasks;
    if (!Objects.isNull(serviceName) && !serviceName.isBlank()) {
      filteredServiceTasks =
          environment.getServicesList().stream()
              .filter(serviceTask -> serviceName.equalsIgnoreCase(serviceTask.getName()))
              .toList();
    } else {
      filteredServiceTasks = environment.getServicesList();
    }
    if (!filteredServiceTasks.isEmpty()) {
      StatusEnvironmentResponse.Builder servicesStatus = StatusEnvironmentResponse.newBuilder();
      servicesStatus.setEnvName(environment.getName()).setEnvStatus(environment.getStatus());

      return Flowable.fromIterable(filteredServiceTasks)
          .flatMap(
              serviceTaskEntity ->
                  getServiceStatus(environment, serviceTaskEntity, userDetails)
                      .map(
                          serviceComponentStatus ->
                              StatusEnvironmentResponse.newBuilder()
                                  .setEnvName(environment.getName())
                                  .setEnvStatus(environment.getStatus())
                                  .addServicesStatus(serviceComponentStatus)
                                  .build()))
          .doOnError(
              throwable ->
                  log.error(
                      "Error while getting  status of services in environment: {}",
                      environment.getName(),
                      throwable));
    } else if (serviceName != null && serviceName.isBlank()) {
      return Flowable.just(
          StatusEnvironmentResponse.newBuilder()
              .setEnvName(environment.getName())
              .setEnvStatus(environment.getStatus())
              .build());
    }
    throw ExceptionUtil.getException(
        OdinError.SERVICE_DOES_NOT_EXIST_IN_ENV, serviceName, environment.getName());
  }

  public Flowable<DeployedServiceStatus> getServiceStatus(
      Environment environment, ServiceTask serviceTask, UserDetails userDetails) {
    return serviceTaskDao
        .getLatestCompletedServiceTask(
            serviceTask.getName(), environment.getName(), userDetails.getOrgId())
        .flatMapPublisher(
            serviceTaskEntity ->
                componentTaskDao
                    .getLatestComponentTasks(serviceTaskEntity)
                    .flatMapPublisher(
                        componentTaskEntities ->
                            // Remove already undeployed components
                            filterUndeployedComponentsAndApplyAction(
                                    environment,
                                    componentTaskEntities,
                                    userDetails,
                                    new ArrayList<>(),
                                    serviceTaskEntity,
                                    Action.HEALTHCHECK)
                                .map(
                                    response ->
                                        DeployedServiceStatus.newBuilder()
                                            .setServiceName(serviceTaskEntity.getName())
                                            .setServiceVersion(
                                                serviceTaskEntity.getServiceVersion())
                                            .setServiceStatus(
                                                response.getServiceStatus().getServiceStatus())
                                            .setLastDeployed(serviceTask.getCreatedAt().toString())
                                            .addAllComponentStatus(
                                                response.getComponentsStatusList().stream()
                                                    .map(
                                                        componentStatus ->
                                                            StatusEnvComponentStatus.newBuilder()
                                                                .setComponentStatus(
                                                                    componentStatus
                                                                        .getComponentStatus())
                                                                .setComponentName(
                                                                    componentStatus
                                                                        .getComponentName())
                                                                .build())
                                                    .toList())
                                            .build())));
  }

  @SneakyThrows
  public Flowable<DeployServiceResponse> deployService(
      DeployServiceRequest request, UserDetails userDetails, String executionId) {
    return executionTaskDao
        .getExecutionByIdAndAction(executionId, Action.DEPLOY.getName())
        .flatMapPublisher(
            executionTaskEntityList ->
                databasePollerService
                    .pollDatabase(
                        request.getServiceDefinition().getName(),
                        request.getEnvName(),
                        userDetails.getOrgId())
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
            deployService(
                    request.getServiceDefinition(),
                    request.getProvisioningConfig(),
                    request.getEnvName(),
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
                    }));
  }
}
