package com.dream11.odin.service;

import static com.dream11.odin.constant.Constants.COMPONENT_NAME_PARAM;
import static com.dream11.odin.constant.Constants.SERVICE_NAME_PARAM;
import static com.dream11.odin.error.OdinError.ENV_DOES_NOT_EXIST;
import static com.dream11.odin.error.OdinError.ENV_NOT_RUNNING;
import static com.dream11.odin.util.EnvironmentUtil.getStatus;

import com.dream11.grpc.error.GrpcException;
import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.ApplicationContext;
import com.dream11.odin.config.AppConfig;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.ComponentStatus;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.constant.EnvironmentStatus;
import com.dream11.odin.constant.ExecTaskType;
import com.dream11.odin.constant.ServiceStatus;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dao.EnvironmentDao;
import com.dream11.odin.dao.ExecutionTaskDao;
import com.dream11.odin.dao.LockDao;
import com.dream11.odin.dto.RequestMetaContext;
import com.dream11.odin.dto.UserDetails;
import com.dream11.odin.dto.constants.RequestMessageType;
import com.dream11.odin.dto.request.RequestMessage;
import com.dream11.odin.dto.v1.AccountInformation;
import com.dream11.odin.dto.v1.Environment;
import com.dream11.odin.dto.v1.EnvironmentSummary;
import com.dream11.odin.entity.EnvironmentAccount;
import com.dream11.odin.entity.EnvironmentEntity;
import com.dream11.odin.error.OdinError;
import com.dream11.odin.grpc.environment.CreateEnvironmentResponse;
import com.dream11.odin.grpc.environment.DeleteEnvironmentResponse;
import com.dream11.odin.grpc.environment.DeployedServiceStatus;
import com.dream11.odin.grpc.environment.DescribeEnvironmentResponse;
import com.dream11.odin.grpc.environment.ListEnvironmentResponse;
import com.dream11.odin.grpc.environment.StatusEnvComponentStatus;
import com.dream11.odin.grpc.environment.StatusEnvironmentResponse;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountRequest;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountResponse;
import com.dream11.odin.grpc.provideraccount.v1.RxProviderAccountServiceGrpc;
import com.dream11.odin.grpc.service.UndeployServiceResponse;
import com.dream11.odin.util.ApplicationUtil;
import com.dream11.odin.util.SingleUtil;
import com.dream11.odin.validations.EnvironmentExistsValidator;
import com.dream11.odin.validations.EnvironmentNameValidator;
import com.dream11.odin.validations.EnvironmentServicesInTerminalStateValidator;
import com.dream11.odin.validations.EnvironmentStateValidatorForDelete;
import com.dream11.odin.validations.Validator;
import com.dream11.queue.producer.MessageProducer;
import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.RxHelper;
import io.vertx.reactivex.core.Vertx;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class EnvironmentBusiness {
  private static final String TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss";
  final AppConfig appConfig;

  final EnvironmentDao environmentDao;
  final LockDao lockDao;
  final ExecutionTaskDao executionTaskDao;

  final MessageProducer<String> messageProducer;

  final RxProviderAccountServiceGrpc.RxProviderAccountServiceStub providerAccountService;

  final PlaceholderService placeholderService;

  final ServiceBusiness serviceBusiness;

  public Single<ListEnvironmentResponse> listEnvironment(
      String userId, Long orgId, Boolean displayAll, String account) {
    return environmentDao
        .getEnvironments(userId, orgId, displayAll, account, true)
        .map(this::buildListEnvResponse);
  }

  public Single<DescribeEnvironmentResponse> describeEnvironment(
      Long orgId, String environmentName, Map<String, String> params) {
    if (environmentName.isEmpty()) {
      throw new GrpcException(OdinError.ENV_NAME_MISSING);
    }
    if (params.containsKey(SERVICE_NAME_PARAM) && params.containsKey(COMPONENT_NAME_PARAM)) {
      return environmentDao
          .getEnvironmentServiceWithComponent(
              orgId,
              environmentName,
              params.get(SERVICE_NAME_PARAM),
              params.get(COMPONENT_NAME_PARAM),
              false)
          .map(
              environment ->
                  DescribeEnvironmentResponse.newBuilder().setEnvironment(environment).build());
    } else if (params.containsKey(SERVICE_NAME_PARAM)
        && !params.containsKey(COMPONENT_NAME_PARAM)) {
      return environmentDao
          .getEnvironmentServiceWithAllComponents(
              orgId, environmentName, params.get(SERVICE_NAME_PARAM))
          .map(
              environment ->
                  DescribeEnvironmentResponse.newBuilder().setEnvironment(environment).build());
    } else if (!params.containsKey(SERVICE_NAME_PARAM)
        && params.containsKey(COMPONENT_NAME_PARAM)) {
      throw new GrpcException(OdinError.PROVIDE_BOTH_SERVICE_AND_COMPONENT);
    }
    return environmentDao
        .getEnvironmentWithServices(orgId, environmentName)
        .map(
            environment ->
                DescribeEnvironmentResponse.newBuilder().setEnvironment(environment).build());
  }

  private ListEnvironmentResponse buildListEnvResponse(List<Environment> envList) {
    ListEnvironmentResponse.Builder listEnvResponseBuilder = ListEnvironmentResponse.newBuilder();
    for (Environment env : envList) {
      for (AccountInformation accountInformation : env.getAccountInformationList()) {
        EnvironmentSummary.Builder envBuilder =
            EnvironmentSummary.newBuilder()
                .setName(env.getName())
                .setState(env.getStatus())
                .setAccount(accountInformation.getProviderAccountName())
                .setCreatedBy(env.getCreatedBy());
        listEnvResponseBuilder.addEnvironments(envBuilder.build());
      }
    }
    return listEnvResponseBuilder.build();
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
        .andThen(getProviderAccountsResponses(accounts))
        .flatMapPublisher(
            providerAccountResponses ->
                Single.just(
                        EnvironmentEntity.builder()
                            .orgId(userDetails.getOrgId())
                            .name(environmentName)
                            .createdBy(userDetails.getUserId())
                            .build())
                    .flatMapMaybe(
                        env -> environmentDao.createEnvAndEnvAccount(env, providerAccountResponses))
                    .switchIfEmpty(Maybe.just(List.of(Optional.empty())))
                    .flatMapPublisher(
                        envAccs ->
                            placeholderService
                                .replacePlaceholdersInEnvironment(
                                    providerAccountResponses,
                                    RequestMetaContext.builder()
                                        .environment(
                                            Environment.newBuilder()
                                                .setName(environmentName)
                                                .build())
                                        .userDetails(userDetails)
                                        .build())
                                .flatMapPublisher(
                                    updatedProviderAccounts ->
                                        provisionEnvironmentAndFetchStatus(
                                            envAccs,
                                            environmentName,
                                            updatedProviderAccounts,
                                            userDetails.getOrgId())))
                    .doOnError(err -> log.error("Error {}", err.getMessage(), err)));
  }

  private Single<List<GetProviderAccountResponse>> getProviderAccountsResponses(
      List<String> accounts) {
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
              log.error("Error fetching details from account manager : {}", err.getMessage());
              return Single.error(
                  ExceptionUtil.getException(
                      OdinError.FAILED_TO_FETCH_ACCOUNT_INFORMATION, err.getMessage()));
            });
  }

  private Flowable<CreateEnvironmentResponse> provisionEnvironmentAndFetchStatus(
      List<Optional<EnvironmentAccount>> environmentAccount,
      String environmentName,
      List<GetProviderAccountResponse> providerAccountResponses,
      Long orgId) {

    long environmentId = -1;

    for (Optional<EnvironmentAccount> envAccount : environmentAccount) {
      if (envAccount.isPresent()) {
        EnvironmentAccount acc = envAccount.get();
        // pushing to SQS if the task is marked IN_PROGRESS
        if (acc.status() == TaskStatus.IN_PROGRESS) {
          GetProviderAccountResponse providerAccountResponse =
              providerAccountResponses.stream()
                  .filter(resp -> resp.getAccount().getName().equals(acc.accountName()))
                  .findFirst()
                  .orElseThrow(
                      () ->
                          ExceptionUtil.getException(
                              OdinError.PROVIDER_NOT_FOUND_FOR_ENV, acc.accountName()));

          pushToSqs(
              environmentName, providerAccountResponse, acc.id(), Action.CREATE_ENVIRONMENT, orgId);

          environmentId = acc.environmentId();
        }
      }
    }
    return environmentId == -1
        ? Flowable.fromArray(
            CreateEnvironmentResponse.newBuilder().setMessage("Env created successfully").build())
        : waitForCreateEnvStatusUpdate(environmentId);
  }

  private Flowable<CreateEnvironmentResponse> waitForCreateEnvStatusUpdate(long envId) {
    return Flowable.interval(appConfig.getEnvDbStatusCheckIntervalSecs(), TimeUnit.SECONDS)
        .flatMap(
            tick ->
                environmentDao
                    .getEnvironmentById(envId)
                    .map(
                        environmentDb -> {
                          if (getStatus(Action.CREATE_ENVIRONMENT, TaskStatus.FAILED)
                              .equals(environmentDb.getStatus())) {
                            throw ExceptionUtil.getException(
                                OdinError.ENV_CREATION_FAILED, environmentDb.getName());
                          }
                          return CreateEnvironmentResponse.newBuilder()
                              .setMessage("Status: " + environmentDb.getStatus())
                              .build();
                        })
                    .toFlowable())
        .takeUntil(
            createEnvironmentResponse ->
                !createEnvironmentResponse
                    .getMessage()
                    .contains(getStatus(Action.CREATE_ENVIRONMENT, TaskStatus.IN_PROGRESS)));
  }

  private Flowable<DeleteEnvironmentResponse> waitForDeleteEnvStatusUpdate(long envId) {
    return Flowable.interval(appConfig.getEnvDbStatusCheckIntervalSecs(), TimeUnit.SECONDS)
        .flatMap(
            tick ->
                environmentDao
                    .getEnvironmentById(envId)
                    .map(
                        environmentDb ->
                            DeleteEnvironmentResponse.newBuilder()
                                .setMessage("Status: " + environmentDb.getStatus())
                                .build())
                    .toFlowable())
        .takeUntil(
            deleteEnvironmentResponse ->
                !deleteEnvironmentResponse
                    .getMessage()
                    .contains(getStatus(Action.DELETE_ENVIRONMENT, TaskStatus.IN_PROGRESS)));
  }

  private void pushToSqs(
      String environmentName,
      GetProviderAccountResponse providerAccountResponse,
      long environmentAccountId,
      Action environmentAction,
      Long orgId) {
    JsonObject requestJson =
        new RequestMessage(
                environmentName,
                providerAccountResponse,
                environmentAction,
                environmentAccountId,
                RequestMessageType.NAMESPACE,
                orgId,
                ApplicationContext.getTraceId())
            .createRequest();

    String finalMessage = requestJson.toString();
    String encodedMessage = ApplicationUtil.compressAndEncode(finalMessage);

    // Insert into execution_tasks table before pushing to SQS
    executionTaskDao
        .createExecutionTask(
            environmentAction.getName(),
            orgId,
            TaskStatus.IN_PROGRESS.getValue(),
            ExecTaskType.ENVIRONMENT.getValue(),
            ApplicationContext.getTraceId(),
            finalMessage,
            ApplicationContext.getUserDetails().getUserId())
        .andThen(SingleUtil.toSingle(messageProducer.send(encodedMessage)).ignoreElement())
        .doOnError(
            err -> {
              log.error("Error while inserting execution task {}", err.getMessage(), err);
              throw new GrpcException(OdinError.INTERNAL_SERVER_ERROR);
            })
        .onErrorComplete()
        .subscribe();
  }

  public Flowable<DeleteEnvironmentResponse> deleteEnvironment(Long orgId, String environmentName) {

    List<Long> envTaskIds = new ArrayList<>();

    Validator validator = new Validator();
    UserDetails userDetails = ApplicationContext.getUserDetails();
    validator.add(
        List.of(
            new EnvironmentStateValidatorForDelete(environmentDao, environmentName),
            new EnvironmentServicesInTerminalStateValidator(environmentDao, environmentName)));
    return validator
        .validateAll()
        .andThen(
            environmentDao
                .getEnvironmentAccounts(environmentName, orgId)
                .map(
                    environmentAccounts ->
                        Pair.of(
                            environmentAccounts.get(0).environmentId(),
                            environmentAccounts.stream()
                                .map(EnvironmentAccount::accountData)
                                .map(
                                    snapshot -> {
                                      GetProviderAccountResponse.Builder
                                          getProviderAccountResponseBuilder =
                                              GetProviderAccountResponse.newBuilder();
                                      try {
                                        JsonFormat.parser()
                                            .merge(snapshot, getProviderAccountResponseBuilder);
                                        return getProviderAccountResponseBuilder.build();
                                      } catch (InvalidProtocolBufferException e) {
                                        throw new GrpcException(OdinError.INVALID_SERVICE_SNAPSHOT);
                                      }
                                    })
                                .toList()))
                .flatMapPublisher(
                    pair ->
                        getEnvironmentEntity(environmentDao.getEnvironmentById(pair.getLeft()))
                            .flatMapMaybe(
                                environmentEntity ->
                                    environmentDao.updateEnvironmentAccount(
                                        pair.getRight(),
                                        environmentEntity,
                                        Action.DELETE_ENVIRONMENT))
                            .flatMapPublisher(
                                envAccs -> {
                                  for (Optional<EnvironmentAccount> envAcc : envAccs) {
                                    envAcc.ifPresent(
                                        environmentAccount ->
                                            envTaskIds.add(environmentAccount.id()));
                                  }

                                  Flowable<List<UndeployServiceResponse>> undeployServicesFlowable =
                                      serviceBusiness
                                          .undeployAllServicesInEnvWithoutValidations(
                                              environmentName, pair.getLeft(), userDetails)
                                          .map(
                                              undeployServiceResponses -> {
                                                log.info(
                                                    "Undeploy service responses: {}",
                                                    undeployServiceResponses);
                                                if (undeployServiceResponses.stream()
                                                    .allMatch(
                                                        undeployServiceResponse ->
                                                            undeployServiceResponse
                                                                .getServiceResponse()
                                                                .getServiceStatus()
                                                                .getServiceStatus()
                                                                .equalsIgnoreCase(
                                                                    TaskStatus.SUCCESSFUL
                                                                        .getValue()))) {
                                                  environmentDao
                                                      .filterAndUpdateEnvironmentAccounts(
                                                          envAccs,
                                                          pair.getRight(),
                                                          Action.DELETE_ENVIRONMENT.name())
                                                      .subscribe(
                                                          nonEmptyClusterAccs -> {
                                                            for (EnvironmentAccount acc :
                                                                nonEmptyClusterAccs) {
                                                              GetProviderAccountResponse resp =
                                                                  pair.getRight().stream()
                                                                      .filter(
                                                                          r ->
                                                                              r.getAccount()
                                                                                  .getName()
                                                                                  .equals(
                                                                                      acc
                                                                                          .accountName()))
                                                                      .findFirst()
                                                                      .orElse(null);
                                                              if (resp != null) {
                                                                pushToSqs(
                                                                    environmentName,
                                                                    resp,
                                                                    acc.id(),
                                                                    Action.DELETE_ENVIRONMENT,
                                                                    orgId);
                                                              }
                                                            }
                                                          },
                                                          err -> {
                                                            log.error(
                                                                "filter and update environment accounts failed",
                                                                err);
                                                            throw new GrpcException(
                                                                OdinError.INTERNAL_SERVER_ERROR);
                                                          });
                                                } else if (undeployServiceResponses.stream()
                                                    .anyMatch(
                                                        undeployServiceResponse ->
                                                            undeployServiceResponse
                                                                .getServiceResponse()
                                                                .getServiceStatus()
                                                                .getServiceStatus()
                                                                .equalsIgnoreCase(
                                                                    TaskStatus.FAILED
                                                                        .getValue()))) {
                                                  updateEnvAccountAsFailed(
                                                          orgId, environmentName, envTaskIds)
                                                      .andThen(
                                                          Flowable.error(
                                                              ExceptionUtil.getException(
                                                                  OdinError.ENV_DELETION_FAILED,
                                                                  environmentName)))
                                                      .onErrorReturn(
                                                          err ->
                                                              DeleteEnvironmentResponse.newBuilder()
                                                                  .setMessage(err.getMessage())
                                                                  .build())
                                                      .subscribe();
                                                }
                                                return undeployServiceResponses;
                                              });

                                  return Flowable.merge(
                                      waitForDeleteEnvStatusUpdate(pair.getLeft()),
                                      undeployServicesFlowable.ignoreElements().toFlowable());
                                })));
  }

  private Completable updateEnvAccountAsFailed(
      Long orgId, String environmentName, List<Long> envTaskIds) {
    return environmentDao
        .getEnvironmentByNameWithAllFields(orgId, environmentName)
        .flatMapCompletable(
            environment ->
                environmentDao
                    .updateEnvironment(
                        orgId,
                        environment.toBuilder().setStatus(TaskStatus.FAILED.getValue()).build())
                    .andThen(
                        environmentDao.updateEnvironmentAccountStatusByIds(
                            envTaskIds, TaskStatus.FAILED.getValue())));
  }

  private Single<EnvironmentEntity> getEnvironmentEntity(Single<Environment> environment) {
    return environment.map(
        env ->
            EnvironmentEntity.builder()
                .orgId(env.getOrgId())
                .id(env.getId())
                .createdBy(env.getCreatedBy())
                .build());
  }

  public Flowable<StatusEnvironmentResponse> getEnvironmentStatus(
      Long orgId, String envName, String serviceName, UserDetails userDetails) {
    Map<String, DeployedServiceStatus> serviceTracker = new HashMap<>();

    return environmentDao
        .getEnvironmentWithServices(orgId, envName)
        .flatMapPublisher(
            environment -> {
              if (environment.getStatus().equalsIgnoreCase(EnvironmentStatus.DELETED.name())) {
                throw ExceptionUtil.getException(ENV_DOES_NOT_EXIST, envName);
              } else if (!environment
                  .getStatus()
                  .equalsIgnoreCase(EnvironmentStatus.RUNNING.name())) {
                throw ExceptionUtil.getException(
                    ENV_NOT_RUNNING, environment.getName(), environment.getStatus());
              }
              return serviceBusiness
                  .getAllServiceStatus(environment, userDetails, serviceName)
                  .flatMap(
                      serviceResponse ->
                          buildStatusEnvironmentResponse(envName, serviceResponse, serviceTracker));
            });
  }

  private Flowable<StatusEnvironmentResponse> buildStatusEnvironmentResponse(
      String envName,
      StatusEnvironmentResponse serviceResponse,
      Map<String, DeployedServiceStatus> serviceTracker) {
    StatusEnvironmentResponse.Builder statusResponseBuilder =
        StatusEnvironmentResponse.newBuilder();
    BinaryOperator<String> statusReducer =
        (status1, status2) -> {
          if (status1.equalsIgnoreCase(TaskStatus.FAILED.toString())
              || status2.equalsIgnoreCase(TaskStatus.FAILED.toString())) {
            return TaskStatus.FAILED.name();
          } else if (status1.equalsIgnoreCase(TaskStatus.IN_PROGRESS.toString())
              || status2.equalsIgnoreCase(TaskStatus.IN_PROGRESS.toString())) {
            return TaskStatus.IN_PROGRESS.name();
          } else {
            return EnvironmentStatus.RUNNING.name();
          }
        };
    serviceResponse
        .getServicesStatusList()
        .forEach(
            service -> {
              Map<String, StatusEnvComponentStatus> componentStatusLiveMap =
                  service.getComponentStatusList().stream()
                      .map(
                          statusEnvComponentStatus ->
                              statusEnvComponentStatus.toBuilder()
                                  .setComponentStatus(
                                      statusEnvComponentStatus
                                              .getComponentStatus()
                                              .equalsIgnoreCase(TaskStatus.SUCCESSFUL.toString())
                                          ? ComponentStatus.RUNNING.name()
                                          : statusEnvComponentStatus.getComponentStatus())
                                  .build())
                      .collect(
                          Collectors.toMap(
                              StatusEnvComponentStatus::getComponentName,
                              statusEnvComponentStatus -> statusEnvComponentStatus));

              String serviceStatus =
                  componentStatusLiveMap.values().stream()
                      .map(StatusEnvComponentStatus::getComponentStatus)
                      .reduce(statusReducer)
                      .orElse(ServiceStatus.RUNNING.name());

              DeployedServiceStatus serviceComponentStatus =
                  DeployedServiceStatus.newBuilder()
                      .setServiceName(service.getServiceName())
                      .setServiceStatus(serviceStatus)
                      .setServiceVersion(service.getServiceVersion())
                      .setLastDeployed(service.getLastDeployed())
                      .addAllComponentStatus(componentStatusLiveMap.values())
                      .build();

              serviceTracker.put(service.getServiceName(), serviceComponentStatus);
            });

    statusResponseBuilder.addAllServicesStatus(serviceTracker.values());
    statusResponseBuilder.setEnvName(envName);
    statusResponseBuilder.setEnvStatus(
        serviceTracker.values().stream()
            .map(DeployedServiceStatus::getServiceStatus)
            .reduce(statusReducer)
            .orElse(EnvironmentStatus.RUNNING.name()));

    return Flowable.just(statusResponseBuilder.build());
  }
}
