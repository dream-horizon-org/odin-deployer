package com.dream11.odin.service;

import static com.dream11.odin.constant.Constants.COMPONENT_NAME_PARAM;
import static com.dream11.odin.constant.Constants.SERVICE_NAME_PARAM;
import static com.dream11.odin.util.EnvironmentUtil.getStatus;

import com.dream11.grpc.error.GrpcException;
import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.ApplicationContext;
import com.dream11.odin.config.AppConfig;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.constant.ExecTaskType;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dao.EnvironmentDao;
import com.dream11.odin.dao.ExecutionTaskDao;
import com.dream11.odin.dao.LockDao;
import com.dream11.odin.dao.ServiceComponentDao;
import com.dream11.odin.dao.TransactionDao;
import com.dream11.odin.dto.RequestMetaContext;
import com.dream11.odin.dto.UserDetails;
import com.dream11.odin.dto.constants.RequestMessageType;
import com.dream11.odin.dto.request.RequestMessage;
import com.dream11.odin.dto.v1.AccountInformation;
import com.dream11.odin.dto.v1.Component;
import com.dream11.odin.dto.v1.Environment;
import com.dream11.odin.dto.v1.EnvironmentSummary;
import com.dream11.odin.dto.v1.Service;
import com.dream11.odin.entity.ComponentEntity;
import com.dream11.odin.entity.EnvironmentAccount;
import com.dream11.odin.entity.EnvironmentEntity;
import com.dream11.odin.entity.EnvironmentEntityWithEnvironmentAccounts;
import com.dream11.odin.entity.EnvironmentServiceEntity;
import com.dream11.odin.error.OdinError;
import com.dream11.odin.grpc.environment.CreateEnvironmentResponse;
import com.dream11.odin.grpc.environment.DeleteEnvironmentResponse;
import com.dream11.odin.grpc.environment.DescribeEnvironmentResponse;
import com.dream11.odin.grpc.environment.ListEnvironmentResponse;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountRequest;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountResponse;
import com.dream11.odin.grpc.provideraccount.v1.RxProviderAccountServiceGrpc;
import com.dream11.odin.grpc.service.UndeployServiceResponse;
import com.dream11.odin.util.ApplicationUtil;
import com.dream11.odin.util.DateTimeUtil;
import com.dream11.odin.util.EnvironmentUtil;
import com.dream11.odin.util.JsonUtil;
import com.dream11.odin.util.SingleUtil;
import com.dream11.odin.validations.EnvironmentExistsValidator;
import com.dream11.odin.validations.EnvironmentNameValidator;
import com.dream11.odin.validations.EnvironmentStateValidatorForDelete;
import com.dream11.odin.validations.Validator;
import com.dream11.queue.producer.MessageProducer;
import com.google.inject.Inject;
import com.google.protobuf.Struct;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.functions.Predicate;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.RxHelper;
import io.vertx.reactivex.core.Vertx;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class EnvironmentBusiness {
  final AppConfig appConfig;

  final EnvironmentDao environmentDao;
  final LockDao lockDao;
  final ExecutionTaskDao executionTaskDao;
  final ServiceComponentDao serviceComponentDao;

  final MessageProducer<String> messageProducer;

  final RxProviderAccountServiceGrpc.RxProviderAccountServiceStub providerAccountService;

  final PlaceholderService placeholderService;

  final ServiceBusiness serviceBusiness;
  final TransactionDao transactionDao;

  public Single<ListEnvironmentResponse> listEnvironment(
      String userId, Long orgId, Boolean displayAll, String account) {
    return this.environmentDao
        .listEnvironments(userId, orgId, displayAll, account)
        .map(this::buildListEnvResponse);
  }

  public Single<DescribeEnvironmentResponse> describeEnvironment(
      Long orgId, String environmentName, Map<String, String> params) {
    Environment.Builder environmentBuilder = Environment.newBuilder();
    return this.environmentDao
        .getEnvironmentWithAccounts(orgId, environmentName)
        .doOnSuccess(
            envWithAccounts -> this.enrichEnvironmentBuilder(environmentBuilder, envWithAccounts))
        .flatMap(
            envWithAccounts -> {
              if (!params.containsKey(SERVICE_NAME_PARAM)
                  && params.containsKey(COMPONENT_NAME_PARAM)) {
                throw new GrpcException(OdinError.PROVIDE_BOTH_SERVICE_AND_COMPONENT);
              }
              if (!params.containsKey(SERVICE_NAME_PARAM)
                  && !params.containsKey(COMPONENT_NAME_PARAM)) {
                return this.environmentDao
                    .getEnvironmentServices(envWithAccounts.getEnvironment().id())
                    .doOnSuccess(
                        environmentServiceEntities ->
                            environmentBuilder.addAllServices(
                                environmentServiceEntities.stream()
                                    .map(
                                        environmentServiceEntity ->
                                            this.buildServiceBuilder(environmentServiceEntity)
                                                .build())
                                    .toList()));
              }

              return (params.containsKey(COMPONENT_NAME_PARAM)
                      ? this.serviceComponentDao.getEnvironmentServiceWithComponent(
                          envWithAccounts.getEnvironment().id(),
                          params.get(SERVICE_NAME_PARAM),
                          params.get(COMPONENT_NAME_PARAM))
                      : this.serviceComponentDao.getEnvironmentServiceWithComponents(
                          envWithAccounts.getEnvironment().id(), params.get(SERVICE_NAME_PARAM)))
                  .doOnSuccess(
                      envServiceWithComponents ->
                          environmentBuilder.addAllServices(
                              List.of(
                                  this.buildServiceBuilder(
                                          envServiceWithComponents.getEnvironmentServiceEntity())
                                      .addAllComponents(
                                          envServiceWithComponents.getComponents().stream()
                                              .map(
                                                  componentEntity ->
                                                      this.buildComponentBuilder(componentEntity)
                                                          .build())
                                              .toList())
                                      .build())));
            })
        .map(
            __ ->
                DescribeEnvironmentResponse.newBuilder()
                    .setEnvironment(environmentBuilder.build())
                    .build());
  }

  private ListEnvironmentResponse buildListEnvResponse(
      List<EnvironmentEntityWithEnvironmentAccounts> envWithAccounts) {
    return ListEnvironmentResponse.newBuilder()
        .addAllEnvironments(
            envWithAccounts.stream()
                .flatMap(
                    envWithAccount ->
                        envWithAccount.getEnvironmentAccounts().stream()
                            .map(
                                ea ->
                                    EnvironmentSummary.newBuilder()
                                        .setName(envWithAccount.getEnvironment().name())
                                        .setCreatedBy(envWithAccount.getEnvironment().createdBy())
                                        .setState(envWithAccount.getEnvironment().status())
                                        .setAccount(ea.accountName())
                                        .build()))
                .toList())
        .build();
  }

  private void enrichEnvironmentBuilder(
      Environment.Builder environmentBuilder,
      EnvironmentEntityWithEnvironmentAccounts envWithAccounts) {
    environmentBuilder
        .setName(envWithAccounts.getEnvironment().name())
        .setCreatedBy(envWithAccounts.getEnvironment().createdBy())
        .setCreatedAt(
            DateTimeUtil.getTimestampFromDateTime(envWithAccounts.getEnvironment().createdAt()))
        .setUpdatedBy(envWithAccounts.getEnvironment().updatedBy())
        .setUpdatedAt(
            DateTimeUtil.getTimestampFromDateTime(envWithAccounts.getEnvironment().updatedAt()))
        .setStatus(envWithAccounts.getEnvironment().status())
        .addAllAccountInformation(
            envWithAccounts.getEnvironmentAccounts().stream()
                .map(
                    environmentAccount ->
                        AccountInformation.newBuilder()
                            .setProviderAccountName(environmentAccount.accountName())
                            .setStatus(
                                EnvironmentUtil.getStatus(
                                    environmentAccount.action(), environmentAccount.status()))
                            .setServiceAccountsSnapshot(
                                JsonUtil.jsonToProtoBuilder(
                                    environmentAccount.accountData(),
                                    GetProviderAccountResponse.newBuilder()))
                            .build())
                .toList());
  }

  private Service.Builder buildServiceBuilder(EnvironmentServiceEntity environmentServiceEntity) {
    return Service.newBuilder()
        .setName(environmentServiceEntity.getServiceName())
        .setStatus(
            EnvironmentUtil.getStatus(
                environmentServiceEntity.getServiceAction(),
                environmentServiceEntity.getServiceStatus()))
        .setConfig(
            JsonUtil.jsonToProtoBuilder(
                environmentServiceEntity.getServiceConfig(), Struct.newBuilder()))
        .setCreatedAt(
            DateTimeUtil.getTimestampFromDateTime(environmentServiceEntity.getCreatedAt()))
        .setCreatedBy(environmentServiceEntity.getCreatedBy())
        .setUpdatedAt(
            DateTimeUtil.getTimestampFromDateTime(environmentServiceEntity.getUpdatedAt()))
        .setUpdatedBy(environmentServiceEntity.getUpdatedBy());
  }

  private Component.Builder buildComponentBuilder(ComponentEntity componentEntity) {
    return Component.newBuilder()
        .setName(componentEntity.getName())
        .setVersion("") // TODO AKSHAY set version
        .setType("") // TODO AKSHAY set type
        .setConfig(JsonUtil.jsonToProtoBuilder(componentEntity.getConfig(), Struct.newBuilder()))
        .setCreatedAt(DateTimeUtil.getTimestampFromDateTime(componentEntity.getCreatedAt()))
        .setCreatedBy(componentEntity.getCreatedBy())
        .setUpdatedAt(DateTimeUtil.getTimestampFromDateTime(componentEntity.getUpdatedAt()))
        .setUpdatedBy(componentEntity.getUpdatedBy());
  }

  public Flowable<CreateEnvironmentResponse> createEnvironment(
      String environmentName, List<String> accounts, UserDetails userDetails) {

    Validator validator = new Validator();
    validator.add(
        Arrays.asList(
            new EnvironmentNameValidator(environmentName),
            new EnvironmentExistsValidator(environmentDao, environmentName, userDetails)));

    return validator
        .validateAll()
        .andThen(this.getProviderAccountsResponses(accounts))
        .flatMapPublisher(
            providerAccountResponses -> {
              EnvironmentEntity environment =
                  EnvironmentEntity.builder()
                      .orgId(userDetails.getOrgId())
                      .name(environmentName)
                      .createdBy(userDetails.getUserId())
                      .build();
              return this.transactionDao
                  .executeTransaction(
                      connection ->
                          this.environmentDao
                              .createEnvironment(connection, environment)
                              .flatMap(
                                  id ->
                                      this.lockDao
                                          .ensureEnvironmentLock(
                                              connection, id, environment.createdBy())
                                          .andThen(
                                              this.lockDao.acquireEnvironmentExclusiveLock(
                                                  connection, id))
                                          .doOnSuccess(
                                              lockAcquired -> {
                                                if (!lockAcquired) {
                                                  throw ExceptionUtil.getException(
                                                      OdinError.ANOTHER_OPERATION_IN_PROGRESS,
                                                      "create environment");
                                                }
                                              })
                                          .ignoreElement()
                                          .andThen(
                                              this.environmentDao.createEnvironmentAccounts(
                                                  connection,
                                                  providerAccountResponses,
                                                  environment.withId(id),
                                                  Action.CREATE_ENVIRONMENT)))
                              .toMaybe())
                  .flatMapPublisher(
                      envAccounts ->
                          this.placeholderService
                              .replacePlaceholdersInEnvironment(
                                  providerAccountResponses,
                                  RequestMetaContext.builder()
                                      .environment(
                                          Environment.newBuilder().setName(environmentName).build())
                                      .userDetails(userDetails)
                                      .build())
                              .flatMapPublisher(
                                  updatedProviderAccounts ->
                                      this.provisionEnvironmentAndFetchStatus(
                                          envAccounts,
                                          environmentName,
                                          updatedProviderAccounts,
                                          userDetails.getOrgId())));
            });
  }

  private Single<List<GetProviderAccountResponse>> getProviderAccountsResponses(
      List<String> accounts) {
    // TODO AKSHAY make this parallel
    return Observable.fromIterable(accounts)
        .flatMap(
            account -> {
              // Add metadata to the request
              Metadata metadata = new Metadata();
              metadata.put(
                  Metadata.Key.of(Constants.ORG_ID_HEADER, Metadata.ASCII_STRING_MARSHALLER),
                  ApplicationContext.getUserDetails().getOrgId().toString());
              return providerAccountService
                  .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                  .getProviderAccount(
                      GetProviderAccountRequest.newBuilder()
                          .setName(account)
                          .setFetchLinkedAccountDetails(Boolean.TRUE)
                          .build())
                  .observeOn(RxHelper.scheduler(Vertx.currentContext()))
                  .toObservable();
            })
        .toList()
        .onErrorResumeNext(
            err -> {
              log.error("Error fetching details from account manager", err);
              return Single.error(
                  ExceptionUtil.getException(
                      OdinError.FAILED_TO_FETCH_ACCOUNT_INFORMATION, err.getMessage()));
            });
  }

  private Flowable<CreateEnvironmentResponse> provisionEnvironmentAndFetchStatus(
      List<EnvironmentAccount> environmentAccounts,
      String environmentName,
      List<GetProviderAccountResponse> providerAccountResponses,
      Long orgId) {

    Map<String, GetProviderAccountResponse> providerAccountResponseMap =
        providerAccountResponses.stream()
            .collect(
                Collectors.toMap(
                    getProviderAccountResponse -> getProviderAccountResponse.getAccount().getName(),
                    getProviderAccountResponse -> getProviderAccountResponse));
    List<Completable> sqsCompletables = new ArrayList<>();
    for (EnvironmentAccount account : environmentAccounts) {
      if (account.status() == TaskStatus.SUCCESSFUL) {
        continue;
      }
      sqsCompletables.add(
          this.pushToSqs(
              environmentName,
              JsonUtil.getJsonFromProto(
                  providerAccountResponseMap.get(account.accountName()).getAccount()),
              account.id(),
              Action.CREATE_ENVIRONMENT,
              orgId));
    }
    return sqsCompletables.isEmpty()
        ? this.lockDao
            .releaseEnvironmentExclusiveLock(environmentAccounts.get(0).environmentId())
            .andThen(
                Flowable.just(
                    CreateEnvironmentResponse.newBuilder()
                        .setMessage("Env created successfully")
                        .build()))
        : Completable.mergeDelayError(sqsCompletables)
            .andThen(
                this.waitForCreateEnvStatusUpdate(
                    environmentAccounts
                        .get(0)
                        .environmentId())); // All environment accounts will have same environment
    // id
  }

  private Flowable<CreateEnvironmentResponse> waitForCreateEnvStatusUpdate(long envId) {
    return Flowable.interval(this.appConfig.getEnvDbStatusCheckIntervalSecs(), TimeUnit.SECONDS)
        .flatMap(
            tick ->
                this.environmentDao
                    .getEnvironmentById(envId)
                    .map(
                        env -> {
                          if (env.getEnvironment()
                              .status()
                              .equals(
                                  EnvironmentUtil.getStatus(
                                      Action.CREATE_ENVIRONMENT, TaskStatus.FAILED))) {
                            throw ExceptionUtil.getException(
                                OdinError.ENV_CREATION_FAILED, env.getEnvironment().name());
                          }
                          return CreateEnvironmentResponse.newBuilder()
                              .setMessage("Status: " + env.getEnvironment().status())
                              .build();
                        })
                    .toFlowable())
        .takeUntil(
            createEnvironmentResponse ->
                !createEnvironmentResponse
                    .getMessage()
                    .contains(
                        EnvironmentUtil.getStatus(
                            Action.CREATE_ENVIRONMENT, TaskStatus.IN_PROGRESS)));
  }

  private Flowable<DeleteEnvironmentResponse> waitForDeleteEnvStatusUpdate(long envId) {
    return Flowable.interval(this.appConfig.getEnvDbStatusCheckIntervalSecs(), TimeUnit.SECONDS)
        .flatMap(
            tick ->
                this.environmentDao
                    .getEnvironmentById(envId)
                    .map(
                        env ->
                            DeleteEnvironmentResponse.newBuilder()
                                .setMessage("Status: " + env.getEnvironment().status())
                                .build())
                    .toFlowable())
        .takeUntil(
            deleteEnvironmentResponse ->
                !deleteEnvironmentResponse
                    .getMessage()
                    .contains(getStatus(Action.DELETE_ENVIRONMENT, TaskStatus.IN_PROGRESS)));
  }

  private Completable pushToSqs(
      String environmentName,
      JsonObject accountData,
      long environmentAccountId,
      Action environmentAction,
      Long orgId) {
    String message =
        new RequestMessage(
                environmentName,
                accountData,
                environmentAction,
                environmentAccountId,
                RequestMessageType.NAMESPACE,
                orgId,
                ApplicationContext.getTraceId())
            .createRequest()
            .toString();

    String encodedMessage = ApplicationUtil.compressAndEncode(message);

    // Insert into execution_tasks table before pushing to SQS
    return this.executionTaskDao
        .createExecutionTask(
            environmentAction.getName(),
            orgId,
            TaskStatus.IN_PROGRESS.getValue(),
            ExecTaskType.ENVIRONMENT.getValue(),
            ApplicationContext.getTraceId(),
            message,
            ApplicationContext.getUserDetails().getUserId())
        .andThen(SingleUtil.toSingle(this.messageProducer.send(encodedMessage)))
        .ignoreElement();
  }

  public Flowable<DeleteEnvironmentResponse> deleteEnvironment(Long orgId, String environmentName) {
    Validator validator = new Validator();
    UserDetails userDetails = ApplicationContext.getUserDetails();
    validator.add(List.of(new EnvironmentStateValidatorForDelete(environmentDao, environmentName)));
    return validator
        .validateAll()
        .andThen(this.environmentDao.getEnvironmentWithAccounts(orgId, environmentName))
        .flatMap(
            envWithAccounts ->
                this.triggerEnvDeletion(envWithAccounts).andThen(Single.just(envWithAccounts)))
        .flatMapPublisher(
            envWithAccounts ->
                this.undeployServicesForEnvDeletion(
                        environmentName, envWithAccounts.getEnvironment().id(), userDetails)
                    .map(
                        undeployServiceResponses ->
                            undeployServiceResponses.stream()
                                .filter(
                                    undeployServiceResponse ->
                                        undeployServiceResponse
                                            .getServiceResponse()
                                            .getServiceStatus()
                                            .getServiceStatus()
                                            .equalsIgnoreCase(TaskStatus.FAILED.getValue()))
                                .toList())
                    .flatMapPublisher(
                        failedUndeployments -> {
                          if (failedUndeployments.isEmpty()) {
                            // Undeploy successful delete namespaces
                            return this.deleteNamespaces(envWithAccounts);
                          } else {
                            // Mark env deletion failure
                            return this.transactionDao
                                .executeTransaction(
                                    connection ->
                                        this.environmentDao
                                            .updateEnvironmentAccounts(
                                                connection,
                                                envWithAccounts.getEnvironment().id(),
                                                Action.DELETE_ENVIRONMENT,
                                                TaskStatus.IN_PROGRESS)
                                            .andThen(
                                                this.lockDao.releaseEnvironmentExclusiveLock(
                                                    envWithAccounts.getEnvironment().id()))
                                            .toMaybe())
                                .flatMapPublisher(
                                    __ ->
                                        Flowable.error(
                                            ExceptionUtil.getException(
                                                OdinError.ENV_DELETION_FAILED, environmentName)));
                          }
                        }));
  }

  private Completable triggerEnvDeletion(EnvironmentEntityWithEnvironmentAccounts envWithAccounts) {
    return this.transactionDao
        .executeTransaction(
            connection ->
                this.lockDao
                    .acquireEnvironmentExclusiveLock(
                        connection, envWithAccounts.getEnvironment().id())
                    .doOnSuccess(
                        lockAcquired -> {
                          if (!lockAcquired) {
                            throw ExceptionUtil.getException(
                                OdinError.ANOTHER_OPERATION_IN_PROGRESS, "delete environment");
                          }
                        })
                    .ignoreElement()
                    .andThen(
                        this.environmentDao.updateEnvironmentAccounts(
                            connection,
                            envWithAccounts.getEnvironment().id(),
                            Action.DELETE_ENVIRONMENT,
                            TaskStatus.IN_PROGRESS))
                    .toMaybe())
        .ignoreElement();
  }

  private Single<List<UndeployServiceResponse>> undeployServicesForEnvDeletion(
      String environmentName, long envId, UserDetails userDetails) {
    return this.serviceBusiness
        .undeployAllServicesInEnvWithoutValidations(environmentName, envId, userDetails)
        .takeUntil(
            (Predicate<? super List<UndeployServiceResponse>>)
                undeployServiceResponses ->
                    undeployServiceResponses.stream()
                        .noneMatch(
                            undeployServiceResponse ->
                                undeployServiceResponse
                                    .getServiceResponse()
                                    .getServiceStatus()
                                    .getServiceStatus()
                                    .equalsIgnoreCase(TaskStatus.IN_PROGRESS.getValue())))
        .lastOrError();
  }

  private Flowable<DeleteEnvironmentResponse> deleteNamespaces(
      EnvironmentEntityWithEnvironmentAccounts envWithAccounts) {

    Map<Boolean, List<EnvironmentAccount>> partitionedEnvAccounts =
        envWithAccounts.getEnvironmentAccounts().stream()
            .collect(
                Collectors.partitioningBy(
                    environmentAccount ->
                        EnvironmentUtil.extractClusters(
                                JsonUtil.jsonToProtoBuilder(
                                        environmentAccount.accountData(),
                                        GetProviderAccountResponse.newBuilder())
                                    .build())
                            .isEmpty()));
    // Mark env accounts without clusters as success
    // TODO AKSHAY release lock if no message in sqs
    return this.environmentDao
        .updateEnvironmentAccountStatusByIds(
            partitionedEnvAccounts.get(true).stream().map(EnvironmentAccount::id).toList(),
            TaskStatus.SUCCESSFUL)
        .andThen(
            Completable.mergeDelayError(
                partitionedEnvAccounts.get(false).stream()
                    .map(
                        environmentAccount ->
                            this.pushToSqs(
                                envWithAccounts.getEnvironment().name(),
                                environmentAccount.accountData(),
                                environmentAccount.id(),
                                Action.DELETE_ENVIRONMENT,
                                envWithAccounts.getEnvironment().orgId()))
                    .toList()))
        .andThen(this.waitForDeleteEnvStatusUpdate(envWithAccounts.getEnvironment().id()));
  }

  // TODO AKSHAY Delete below this

  //  public Flowable<StatusEnvironmentResponse> getEnvironmentStatus(
  //      Long orgId, String envName, String serviceName, UserDetails userDetails) {
  //    Map<String, DeployedServiceStatus> serviceTracker = new HashMap<>();
  //
  //    return environmentDao
  //        .getEnvironmentWithServices(orgId, envName)
  //        .flatMapPublisher(
  //            environment -> {
  //              if (environment.getStatus().equalsIgnoreCase(EnvironmentStatus.DELETED.name())) {
  //                throw ExceptionUtil.getException(ENV_DOES_NOT_EXIST, envName);
  //              } else if (!environment
  //                  .getStatus()
  //                  .equalsIgnoreCase(EnvironmentStatus.RUNNING.name())) {
  //                throw ExceptionUtil.getException(
  //                    ENV_NOT_RUNNING, environment.getName(), environment.getStatus());
  //              }
  //              return serviceBusiness
  //                  .getAllServiceStatus(environment, userDetails, serviceName)
  //                  .flatMap(
  //                      serviceResponse ->
  //                          buildStatusEnvironmentResponse(envName, serviceResponse,
  // serviceTracker));
  //            });
  //  }
  //
  //  private Flowable<StatusEnvironmentResponse> buildStatusEnvironmentResponse(
  //      String envName,
  //      StatusEnvironmentResponse serviceResponse,
  //      Map<String, DeployedServiceStatus> serviceTracker) {
  //    StatusEnvironmentResponse.Builder statusResponseBuilder =
  //        StatusEnvironmentResponse.newBuilder();
  //    BinaryOperator<String> statusReducer =
  //        (status1, status2) -> {
  //          if (status1.equalsIgnoreCase(TaskStatus.FAILED.toString())
  //              || status2.equalsIgnoreCase(TaskStatus.FAILED.toString())) {
  //            return TaskStatus.FAILED.name();
  //          } else if (status1.equalsIgnoreCase(TaskStatus.IN_PROGRESS.toString())
  //              || status2.equalsIgnoreCase(TaskStatus.IN_PROGRESS.toString())) {
  //            return TaskStatus.IN_PROGRESS.name();
  //          } else {
  //            return EnvironmentStatus.RUNNING.name();
  //          }
  //        };
  //    serviceResponse
  //        .getServicesStatusList()
  //        .forEach(
  //            service -> {
  //              Map<String, StatusEnvComponentStatus> componentStatusLiveMap =
  //                  service.getComponentStatusList().stream()
  //                      .map(
  //                          statusEnvComponentStatus ->
  //                              statusEnvComponentStatus.toBuilder()
  //                                  .setComponentStatus(
  //                                      statusEnvComponentStatus
  //                                              .getComponentStatus()
  //
  // .equalsIgnoreCase(TaskStatus.SUCCESSFUL.toString())
  //                                          ? ComponentStatus.RUNNING.name()
  //                                          : statusEnvComponentStatus.getComponentStatus())
  //                                  .build())
  //                      .collect(
  //                          Collectors.toMap(
  //                              StatusEnvComponentStatus::getComponentName,
  //                              statusEnvComponentStatus -> statusEnvComponentStatus));
  //
  //              String serviceStatus =
  //                  componentStatusLiveMap.values().stream()
  //                      .map(StatusEnvComponentStatus::getComponentStatus)
  //                      .reduce(statusReducer)
  //                      .orElse(ServiceStatus.RUNNING.name());
  //
  //              DeployedServiceStatus serviceComponentStatus =
  //                  DeployedServiceStatus.newBuilder()
  //                      .setServiceName(service.getServiceName())
  //                      .setServiceStatus(serviceStatus)
  //                      .setServiceVersion(service.getServiceVersion())
  //                      .setLastDeployed(service.getLastDeployed())
  //                      .addAllComponentStatus(componentStatusLiveMap.values())
  //                      .build();
  //
  //              serviceTracker.put(service.getServiceName(), serviceComponentStatus);
  //            });
  //
  //    statusResponseBuilder.addAllServicesStatus(serviceTracker.values());
  //    statusResponseBuilder.setEnvName(envName);
  //    statusResponseBuilder.setEnvStatus(
  //        serviceTracker.values().stream()
  //            .map(DeployedServiceStatus::getServiceStatus)
  //            .reduce(statusReducer)
  //            .orElse(EnvironmentStatus.RUNNING.name()));
  //
  //    return Flowable.just(statusResponseBuilder.build());
  //  }
}
