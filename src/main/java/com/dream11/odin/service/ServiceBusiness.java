package com.dream11.odin.service;

import static com.dream11.odin.constant.Constants.COMPONENT_CONFIG_KEY;
import static com.dream11.odin.constant.Constants.PROVISIONING_CONFIG_KEY;

import com.dream11.grpc.error.GrpcException;
import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.ApplicationContext;
import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.constant.ServiceOperations;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dao.ComponentTaskDao;
import com.dream11.odin.dao.ComponentValidateTaskDao;
import com.dream11.odin.dao.EnvironmentDao;
import com.dream11.odin.dao.ServiceTaskDao;
import com.dream11.odin.dao.ServiceValidateTaskDao;
import com.dream11.odin.dto.ComponentData;
import com.dream11.odin.dto.ComponentId;
import com.dream11.odin.dto.ModifiedComponentData;
import com.dream11.odin.dto.RequestMetaContext;
import com.dream11.odin.dto.ServiceData;
import com.dream11.odin.dto.UserDetails;
import com.dream11.odin.dto.requestqueue.ComponentAction;
import com.dream11.odin.dto.requestqueue.ServiceRequestQueueMessage;
import com.dream11.odin.dto.requestqueue.Stage;
import com.dream11.odin.dto.response.OperateResponse;
import com.dream11.odin.dto.response.StatusResponse;
import com.dream11.odin.dto.v1.AccountInformation;
import com.dream11.odin.dto.v1.ComponentDefinition;
import com.dream11.odin.dto.v1.ComponentProvisioningConfig;
import com.dream11.odin.dto.v1.Environment;
import com.dream11.odin.dto.v1.ProvisioningConfig;
import com.dream11.odin.dto.v1.ServiceDefinition;
import com.dream11.odin.dto.v1.ServiceTask;
import com.dream11.odin.entity.ComponentTaskEntity;
import com.dream11.odin.entity.ComponentValidateTaskEntity;
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
import com.dream11.odin.service.operation.AddComponentServiceOperation;
import com.dream11.odin.service.operation.ComponentOperation;
import com.dream11.odin.service.operation.Operation;
import com.dream11.odin.service.operation.RemoveComponentServiceOperation;
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
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ServiceBusiness {
  public static final String DEFAULT = "<default>";
  public static final String ERROR_FIELD = "error";
  public static final String DEPENDENT_COMPONENT_FAILURE_ERROR =
      "Component execution failed due to dependent component failure";

  final ComponentTaskDao componentTaskDao;
  final DatabasePollerService databasePollerService;
  final ComponentValidateTaskDao componentValidateTaskDao;
  final ServiceValidateTaskDao serviceValidateTaskDao;
  final EnvironmentDao environmentDao;
  final MessageProducer<String> messageProducer;
  final MysqlClient mysqlClient;
  final ServiceTaskDao serviceTaskDao;
  final PlaceholderService placeholderService;
  final InterceptorService interceptorService;
  final ComponentEnrichmentService componentEnrichmentService;

  private Flowable<DeployServiceResponse> deployService(
      ServiceDefinition serviceDefinition,
      ProvisioningConfig provisioningConfig,
      String envName,
      UserDetails userDetails) {
    ServiceData serviceData =
        ServiceData.builder()
            .serviceDefinition(serviceDefinition)
            .componentProvisioningConfigs(provisioningConfig.getComponentProvisioningConfigList())
            .build();
    return ValidationUtil.validateService(serviceData)
        // Validate env state to be RUNNING
        .andThen(ValidationUtil.validateEnvState(environmentDao, envName, userDetails))
        .andThen(
            this.environmentDao.getEnvironmentByNameWithAllFields(userDetails.getOrgId(), envName))
        .flatMapCompletable(
            environment ->
                ValidationUtil.validateDeploymentTypePrefix(
                    provisioningConfig, environment.getAccountInformationList(), envName))
        .andThen(environmentDao.getEnvironmentByNameWithAllFields(userDetails.getOrgId(), envName))
        .toFlowable()
        .flatMap(
            environment -> {
              List<AccountInformation> environmentProviderAccounts =
                  environment.getAccountInformationList();
              Map<String, Action> componentDeployActions =
                  ComponentUtil.getAllComponentActions(serviceData, Action.DEPLOY);
              Map<ComponentId, ComponentData> componentDataMap =
                  ComponentUtil.getAllComponentsData(
                      serviceData, environmentProviderAccounts, componentDeployActions);

              RequestMetaContext requestMetaContext =
                  RequestMetaContext.builder()
                      .serviceName(serviceDefinition.getName())
                      .environment(environment)
                      .userDetails(userDetails)
                      .additionalContext(Map.of(Constants.ACTION, Constants.DEPLOY_ACTION))
                      .build();

              return interceptorService
                  .invokeInterceptors(
                      componentEnrichmentService.addOdinDiscoveryData(componentDataMap),
                      requestMetaContext)
                  .flatMap(
                      interceptedComponentMap ->
                          placeholderService.replacePlaceholdersInComponents(
                              interceptedComponentMap, requestMetaContext))
                  .flatMapPublisher(
                      updatedComponentMap -> {
                        log.info(
                            "Updated the componentMap using interceptors for deploy for env {}, proceeding",
                            envName);
                        return serviceTaskDao
                            .getLatestNonHealthcheckServiceTask(
                                environment.getId(), serviceDefinition.getName())
                            .flatMapPublisher(
                                serviceTaskEntity -> {
                                  // If deploy is IN_PROGRESS -> resume deployment
                                  if (ServiceUtil.isDeployInProgress(serviceTaskEntity)) {
                                    return checkConfigAndResumeDeployment(
                                            serviceData, serviceTaskEntity)
                                        .map(
                                            serviceResponse ->
                                                DeployServiceResponse.newBuilder()
                                                    .setServiceResponse(serviceResponse)
                                                    .build());
                                  }
                                  return checkDeploymentStatus(
                                          serviceDefinition.getName(), serviceTaskEntity)
                                      .flatMapPublisher(
                                          hasFailedDeployment -> {
                                            // If DEPLOY is FAILED -> create updated actions and
                                            // re-deploy
                                            if (hasFailedDeployment.equals(Boolean.TRUE)) {
                                              return serviceTaskDao
                                                  .getLatestCompletedServiceTask(
                                                      environment.getId(),
                                                      serviceDefinition.getName())
                                                  .flatMapPublisher(
                                                      latestServiceTask ->
                                                          reTriggerFailedDeployment(
                                                                  serviceData,
                                                                  updatedComponentMap,
                                                                  environment,
                                                                  userDetails,
                                                                  latestServiceTask)
                                                              .map(
                                                                  serviceResponse ->
                                                                      DeployServiceResponse
                                                                          .newBuilder()
                                                                          .setServiceResponse(
                                                                              serviceResponse)
                                                                          .build()));
                                              // If undeploy is SUCCESSFUL -> trigger new
                                              // deployment
                                            } else {
                                              return validateAndDeploy(
                                                      serviceData,
                                                      updatedComponentMap,
                                                      environment,
                                                      userDetails)
                                                  .map(
                                                      serviceResponse ->
                                                          DeployServiceResponse.newBuilder()
                                                              .setServiceResponse(serviceResponse)
                                                              .build());
                                            }
                                          });
                                })
                            // If no service task is found -> trigger new deployment
                            .switchIfEmpty(
                                validateAndDeploy(
                                        serviceData, updatedComponentMap, environment, userDetails)
                                    .map(
                                        serviceResponse ->
                                            DeployServiceResponse.newBuilder()
                                                .setServiceResponse(serviceResponse)
                                                .build()));
                      });
            });
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

  protected Flowable<ServiceResponse> checkConfigAndResumeDeployment(
      ServiceData serviceData, ServiceTaskEntity serviceTaskEntity) {
    if (DigestUtils.sha256Hex(ServiceUtil.createServiceProvisioningConfigJson(serviceData).encode())
        .equals(serviceTaskEntity.getServiceConfigHash())) {
      return databasePollerService.pollDatabase(serviceTaskEntity.getId(), Action.DEPLOY);
    }
    return Flowable.error(ExceptionUtil.getException(OdinError.DUPLICATE_SERVICE_DEPLOYMENT));
  }

  protected Flowable<ServiceResponse> reTriggerFailedDeployment(
      ServiceData serviceData,
      Map<ComponentId, ComponentData> componentDataMap,
      Environment environment,
      UserDetails userDetails,
      ServiceTaskEntity serviceTaskEntity) {
    return validate(
            serviceData,
            ComponentUtil.getComponentDataMapForAction(componentDataMap, Action.VALIDATE),
            environment,
            userDetails)
        .flatMap(
            validateResponse -> {
              if (ValidationUtil.isValidateSuccessful(validateResponse.getServiceStatus())) {
                return Flowable.merge(
                    Flowable.just(validateResponse),
                    getModifiedComponentDataActionMap(serviceTaskEntity, componentDataMap)
                        .map(
                            mapListPair ->
                                Pair.of(
                                    mapListPair.getLeft(),
                                    ActionUtil.filterSkippedDependencies(mapListPair.getRight())))
                        .toFlowable()
                        .flatMap(
                            pair ->
                                applyActionToService(
                                    serviceData,
                                    pair.getLeft(),
                                    environment,
                                    pair.getRight(),
                                    userDetails,
                                    Action.DEPLOY,
                                    serviceTaskEntity.getVersion()))
                        .switchIfEmpty(
                            Flowable.error(
                                new IllegalStateException(
                                    "All components deployed successfully"))));
              }
              return Flowable.just(validateResponse);
            });
  }

  protected Single<Pair<Map<ComponentId, ComponentData>, List<ComponentAction>>>
      getModifiedComponentDataActionMap(
          ServiceTaskEntity serviceTask, Map<ComponentId, ComponentData> componentDataMap) {

    List<ComponentAction> componentActions =
        ActionUtil.buildComponentActions(componentDataMap, Action.DEPLOY, new HashMap<>());
    return componentTaskDao
        .getFailedOrSuccessServiceComponents(serviceTask)
        .map(
            componentTaskEntities -> {
              List<ComponentAction> updatedComponentActions = new ArrayList<>();
              Map<ComponentId, ComponentData> updatedComponentData = new HashMap<>();

              // Get component which are not executed by orchestrator
              Set<String> unExecutedComponents =
                  ComponentUtil.getUnexecutedComponentNameSet(componentTaskEntities);

              log.info("Not executed components: {}", unExecutedComponents);

              componentTaskEntities.forEach(
                  componentTaskEntity -> {
                    ModifiedComponentData modifiedComponentData =
                        new ModifiedComponentData(
                            componentTaskEntity, componentDataMap, componentActions);

                    /*
                     * If component task is marked failed because its execution was never triggered
                     * by orchestrator (This could be due to failure of parent/dependent component), then simply
                     * trigger deploy for that component
                     */
                    if (unExecutedComponents.contains(componentTaskEntity.getComponentName())) {
                      updatedComponentActions.add(modifiedComponentData.getDeployComponentAction());
                      updatedComponentData.putIfAbsent(
                          modifiedComponentData.getDeployComponentId(),
                          modifiedComponentData.getNewComponentData());
                      return;
                    }

                    // If undeploy FAILED for a component, (implying odin config was changed)
                    // Undeploy the component again
                    if (componentTaskEntity.getAction().equals(Action.UNDEPLOY)) {
                      if (componentTaskEntity.getStatus().equals(TaskStatus.FAILED)) {
                        updatedComponentActions.add(
                            modifiedComponentData.getUndeployComponentAction());
                        updatedComponentData.putIfAbsent(
                            modifiedComponentData.getUndeployComponentId(),
                            modifiedComponentData.getOldComponentData());
                      }
                      return;
                    }

                    // Definition does not contain component. Undeploy the component
                    if (!componentDataMap.containsKey(
                        modifiedComponentData.getDeployComponentId())) {
                      updatedComponentActions.add(
                          modifiedComponentData.getUndeployComponentAction());
                      updatedComponentData.putIfAbsent(
                          modifiedComponentData.getUndeployComponentId(),
                          modifiedComponentData.getOldComponentData());
                      return;
                    }

                    // New definition contain component with modified config.
                    handleModifiedConfig(
                        updatedComponentActions, updatedComponentData, modifiedComponentData);

                    // New definition contain component with same config.
                    handleSameConfig(
                        componentTaskEntity,
                        updatedComponentActions,
                        updatedComponentData,
                        modifiedComponentData);
                  });
              // Add newly added component present in service definition but not
              // present in old service definition
              Set<String> oldComponentNames =
                  componentTaskEntities.stream()
                      .map(ComponentTaskEntity::getComponentName)
                      .collect(Collectors.toSet());
              Set<String> newComponentNames =
                  componentDataMap.keySet().stream()
                      .map(ComponentId::getComponentName)
                      .collect(Collectors.toSet());
              // Creates set of missing component names
              newComponentNames.removeAll(oldComponentNames);

              newComponentNames.forEach(
                  componentName -> {
                    ComponentId componentId =
                        ComponentUtil.buildComponentId(componentName, Action.DEPLOY);

                    updatedComponentData.put(componentId, componentDataMap.get(componentId));
                    ComponentAction componentAction =
                        ComponentUtil.getComponentAction(
                            componentDataMap.get(componentId).getComponentDefinition(),
                            componentDataMap.get(componentId).getComponentProvisioningConfig(),
                            componentDataMap.get(componentId).getEnvironmentProviderAccounts(),
                            Stage.builder().name(Action.DEPLOY).config(new HashMap<>()).build());
                    updatedComponentActions.add(
                        componentAction.addDependsOn(
                            ComponentUtil.getComponentDependencies(
                                componentId, componentDataMap, componentActions)));
                  });
              return Pair.of(updatedComponentData, updatedComponentActions);
            });
  }

  public Flowable<ServiceResponse> applyActionToService(
      ServiceData serviceData,
      Map<ComponentId, ComponentData> componentDataMap,
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

  public Flowable<ServiceResponse> validateAndDeploy(
      ServiceData serviceData,
      Map<ComponentId, ComponentData> componentDataMap,
      Environment environment,
      UserDetails userDetails) {
    // Create validate task and trigger validate

    return validate(
            serviceData,
            ComponentUtil.getComponentDataMapForAction(componentDataMap, Action.VALIDATE),
            environment,
            userDetails)
        .flatMap(
            validateResponse -> {
              // If VALIDATE is SUCCESSFUL, trigger DEPLOY
              if (ValidationUtil.isValidateSuccessful(validateResponse.getServiceStatus())) {
                log.info(
                    "Validation successful, proceeding with deployment for service {}",
                    serviceData.getServiceDefinition().getName());
                return serviceTaskDao
                    .getLatestNonHealthcheckServiceTask(
                        environment.getId(), serviceData.getServiceDefinition().getName())
                    .flatMapPublisher(
                        serviceTaskEntity -> {
                          // If deploy is IN_PROGRESS -> resume deployment
                          if (ServiceUtil.isDeployInProgress(serviceTaskEntity)) {
                            return checkConfigAndResumeDeployment(serviceData, serviceTaskEntity);
                          } else {
                            return deploy(
                                serviceData,
                                componentDataMap,
                                environment,
                                userDetails,
                                validateResponse);
                          }
                        })
                    .switchIfEmpty(
                        deploy(
                            serviceData,
                            componentDataMap,
                            environment,
                            userDetails,
                            validateResponse));
              }
              return Flowable.just(validateResponse);
            });
  }

  protected Flowable<ServiceResponse> deploy(
      ServiceData serviceData,
      Map<ComponentId, ComponentData> componentDataMap,
      Environment environment,
      UserDetails userDetails,
      ServiceResponse validateResponse) {

    return serviceTaskDao
        .getLatestCompletedServiceTask(
            environment.getId(), serviceData.getServiceDefinition().getName())
        .flatMapPublisher(
            latestServiceTask ->
                applyDeployAction(
                    serviceData,
                    componentDataMap,
                    environment,
                    userDetails,
                    validateResponse,
                    latestServiceTask.getVersion()))
        .switchIfEmpty(
            applyDeployAction(
                serviceData, componentDataMap, environment, userDetails, validateResponse, 0));
  }

  protected Flowable<ServiceResponse> applyDeployAction(
      ServiceData serviceData,
      Map<ComponentId, ComponentData> componentDataMap,
      Environment environment,
      UserDetails userDetails,
      ServiceResponse validateResponse,
      int prevServiceTaskEntityVersion) {
    List<ComponentAction> deployComponentAction =
        ActionUtil.buildComponentActions(componentDataMap, Action.DEPLOY, new HashMap<>());
    return Flowable.merge(
        Flowable.just(validateResponse),
        applyActionToService(
            serviceData,
            componentDataMap,
            environment,
            deployComponentAction,
            userDetails,
            Action.DEPLOY,
            prevServiceTaskEntityVersion));
  }

  protected Maybe<List<ComponentTaskEntity>> createServiceAndComponentTasks(
      ServiceData serviceData,
      Map<ComponentId, ComponentData> componentDataMap,
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

  // Returns true if DEPLOY is failed, false if UNDEPLOY is successful
  protected Single<Boolean> checkDeploymentStatus(
      String serviceName, ServiceTaskEntity serviceTaskEntity) {
    switch (serviceTaskEntity.getActions()) {
      case DEPLOY -> {
        // DEPLOY IN_PROGRESS is unreachable. It has been handled
        if (serviceTaskEntity.getStatus().equals(TaskStatus.SUCCESSFUL)) {
          return Single.error(
              ExceptionUtil.getException(
                  OdinError.CANNOT_DEPLOY_OVER_ACTION_WITH_STATUS,
                  serviceName,
                  serviceTaskEntity.getActions(),
                  serviceTaskEntity.getStatus(),
                  Constants.USE_OPERATE));
        }
        return Single.just(Boolean.TRUE);
      }

      case UNDEPLOY -> {
        if (serviceTaskEntity.getStatus().equals(TaskStatus.SUCCESSFUL)) {
          return Single.just(Boolean.FALSE);
        }
        return Single.error(
            ExceptionUtil.getException(
                OdinError.CANNOT_DEPLOY_OVER_ACTION_WITH_STATUS,
                serviceName,
                serviceTaskEntity.getActions(),
                serviceTaskEntity.getStatus(),
                Constants.UNDEPLOY_AGAIN));
      }

      case OPERATE -> {
        return Single.error(
            ExceptionUtil.getException(
                OdinError.CANNOT_DEPLOY_OVER_ACTION_WITH_STATUS,
                serviceName,
                serviceTaskEntity.getActions(),
                serviceTaskEntity.getStatus(),
                Constants.USE_OPERATE));
      }

      default -> {
        return Single.error(
            ExceptionUtil.getException(OdinError.INVALID_ACTION, serviceTaskEntity.getActions()));
      }
    }
  }

  public Flowable<ServiceResponse> validate(
      ServiceData serviceData,
      Map<ComponentId, ComponentData> componentDataMap,
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
      Map<ComponentId, ComponentData> componentDataMap,
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

  private void handleModifiedConfig(
      List<ComponentAction> updatedComponentActions,
      Map<ComponentId, ComponentData> updatedComponentData,
      ModifiedComponentData modifiedComponentData) {
    if (ServiceUtil.isOdinConfigModified(
        modifiedComponentData.getOldComponentData(), modifiedComponentData.getNewComponentData())) {
      // undeploy old component
      updatedComponentActions.add(modifiedComponentData.getUndeployComponentAction());
      updatedComponentData.putIfAbsent(
          modifiedComponentData.getUndeployComponentId(),
          modifiedComponentData.getOldComponentData());
    }
    if (ServiceUtil.isConfigModified(
        modifiedComponentData.getOldComponentData(), modifiedComponentData.getNewComponentData())) {
      // deploy new component
      updatedComponentActions.add(modifiedComponentData.getDeployComponentAction());
      updatedComponentData.putIfAbsent(
          modifiedComponentData.getDeployComponentId(),
          modifiedComponentData.getNewComponentData());
    }
  }

  private void handleSameConfig(
      ComponentTaskEntity componentTaskEntity,
      List<ComponentAction> updatedComponentActions,
      Map<ComponentId, ComponentData> updatedComponentData,
      ModifiedComponentData modifiedComponentData) {
    if (!ServiceUtil.isConfigModified(
        modifiedComponentData.getOldComponentData(), modifiedComponentData.getNewComponentData())) {
      if (componentTaskEntity.getStatus().equals(TaskStatus.FAILED)) {
        // deploy component
        updatedComponentActions.add(modifiedComponentData.getDeployComponentAction());
        updatedComponentData.putIfAbsent(
            modifiedComponentData.getDeployComponentId(),
            modifiedComponentData.getNewComponentData());
      } else {
        // Skip deployment
        // Add data to create tasks but don't add action
        updatedComponentData.putIfAbsent(
            modifiedComponentData.getDeployComponentId(),
            modifiedComponentData.getNewComponentData());
      }
    }
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
                      .setConfigJson(updatedComponentData.getOperationConfigJson())
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
                      .setConfigJson(componentData.getOperationConfigJson())
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
    return !JsonUtil.convertToJsonSorted(operateServiceRequest.getConfigJson())
        .equals(
            JsonUtil.sortJsonObject(
                componentTaskEntity.getConfig().getJsonObject("operationConfig")));
  }

  private boolean isAddComponentConfigDifferent(
      OperateServiceRequest operateServiceRequest, ComponentTaskEntity componentTaskEntity) {
    JsonObject request = new JsonObject(operateServiceRequest.getConfigJson());

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

    Map<ComponentId, ComponentData> componentDataMap =
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
      String configJson) {
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
                                  .setConfigJson(configJson)
                                  .build();

                          Pair<JsonObject, JsonObject> diff =
                              compareConfigs(
                                  new JsonObject(component.getConfigJson()),
                                  new JsonObject(operateServiceRequest.getConfigJson()));
                          return OperateComponentDiffResponse.newBuilder()
                              .setOldValuesJson(diff.getLeft().encode())
                              .setNewValuesJson(diff.getRight().encode())
                              .build();
                        }));
  }

  private Pair<JsonObject, JsonObject> compareConfigs(JsonObject oldConfig, JsonObject newConfig) {
    JsonObject oldDiffBuilder = new JsonObject();
    JsonObject newDiffBuilder = new JsonObject();

    newConfig.forEach(
        entry -> {
          String key = entry.getKey();
          Object newValue = entry.getValue();
          Object oldValue = oldConfig.getValue(key);
          processField(key, newValue, oldValue, oldDiffBuilder, newDiffBuilder);
        });

    return Pair.of(oldDiffBuilder, newDiffBuilder);
  }

  private void processField(
      String key,
      Object newValue,
      Object oldValue,
      JsonObject oldDiffBuilder,
      JsonObject newDiffBuilder) {
    if (oldValue != null) {
      handleExistingOldValue(key, newValue, oldValue, oldDiffBuilder, newDiffBuilder);
    } else {
      handleNullOldValue(key, newValue, oldDiffBuilder, newDiffBuilder);
    }
  }

  private void handleExistingOldValue(
      String key,
      Object newValueObject,
      Object oldValueObject,
      JsonObject oldDiffBuilder,
      JsonObject newDiffBuilder) {
    if (!oldValueObject.equals(newValueObject)) {
      if (oldValueObject instanceof JsonObject oldValue
          && newValueObject instanceof JsonObject newValue) {
        Pair<JsonObject, JsonObject> nestedDiff = compareConfigs(oldValue, newValue);
        addNestedDiffs(key, nestedDiff, oldDiffBuilder, newDiffBuilder);
      } else {
        oldDiffBuilder.put(key, oldValueObject);
        newDiffBuilder.put(key, newValueObject);
      }
    }
  }

  private void handleNullOldValue(
      String key, Object newValueObject, JsonObject oldDiffBuilder, JsonObject newDiffBuilder) {
    if (newValueObject instanceof JsonObject newValue) {
      oldDiffBuilder.put(key, newValue);
    } else {
      oldDiffBuilder.put(key, new JsonObject());
    }
    newDiffBuilder.put(key, newValueObject);
  }

  private void addNestedDiffs(
      String key,
      Pair<JsonObject, JsonObject> nestedDiff,
      JsonObject oldDiffBuilder,
      JsonObject newDiffBuilder) {
    if (!nestedDiff.getLeft().isEmpty()) {
      oldDiffBuilder.put(key, nestedDiff.getLeft());
    }
    if (!nestedDiff.getRight().isEmpty()) {
      newDiffBuilder.put(key, nestedDiff.getRight());
    }
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
  public Flowable<DeployServiceResponse> deployService(Single<DeployServiceRequest> request) {
    return request.flatMapPublisher(
        deployServiceRequest ->
            serviceTaskDao
                .getServiceTaskByTraceIdServiceNameEnvNameAndAction(
                    ApplicationContext.getTraceId(),
                    deployServiceRequest.getServiceDefinition().getName(),
                    deployServiceRequest.getEnvName())
                .flatMapPublisher(
                    serviceTaskEntity ->
                        databasePollerService
                            .pollDatabase(serviceTaskEntity.getId(), Action.DEPLOY)
                            .map(
                                serviceResponse -> {
                                  log.info(
                                      "Found response for traceId: {}, serviceName: {}, envName: {}",
                                      ApplicationContext.getTraceId(),
                                      deployServiceRequest.getServiceDefinition().getName(),
                                      deployServiceRequest.getEnvName());
                                  return DeployServiceResponse.newBuilder()
                                      .setServiceResponse(serviceResponse)
                                      .build();
                                }))
                .switchIfEmpty(
                    request.flatMapPublisher(
                        req ->
                            request
                                .toFlowable()
                                .flatMap(
                                    transformedReq ->
                                        this.deployService(
                                                transformedReq.getServiceDefinition(),
                                                transformedReq.getProvisioningConfig(),
                                                transformedReq.getEnvName(),
                                                ApplicationContext.getUserDetails())
                                            .map(
                                                response -> {
                                                  if (response.hasServiceResponse()) {
                                                    return response.toBuilder()
                                                        .setServiceResponse(
                                                            response
                                                                .getServiceResponse()
                                                                .toBuilder()
                                                                .setName(
                                                                    transformedReq
                                                                        .getServiceDefinition()
                                                                        .getName())
                                                                .setVersion(
                                                                    transformedReq
                                                                        .getServiceDefinition()
                                                                        .getVersion())
                                                                .build())
                                                        .build();
                                                  }
                                                  return response;
                                                })))));
  }
}
