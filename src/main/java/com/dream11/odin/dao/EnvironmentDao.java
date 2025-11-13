package com.dream11.odin.dao;

import static com.dream11.odin.constant.Constants.COL_ACTION_NAME;
import static com.dream11.odin.constant.Constants.CREATED_AT;
import static com.dream11.odin.constant.Constants.EKS_PROVIDER_SERVICE_CATEGORY;
import static com.dream11.odin.constant.Constants.SERVICE_NAME;
import static com.dream11.odin.constant.Constants.STATUS;
import static com.dream11.odin.constant.Constants.UPDATED_AT;
import static com.dream11.odin.dao.query.MysqlQuery.ALL;
import static com.dream11.odin.dao.query.MysqlQuery.BY_ACCOUNT;
import static com.dream11.odin.dao.query.MysqlQuery.BY_ENVIRONMENT_NAME;
import static com.dream11.odin.dao.query.MysqlQuery.BY_USER;
import static com.dream11.odin.dao.query.MysqlQuery.CREATE_ENVIRONMENT;
import static com.dream11.odin.dao.query.MysqlQuery.CREATE_ENVIRONMENT_TASK;
import static com.dream11.odin.dao.query.MysqlQuery.ENVIRONMENT_BY_ID;
import static com.dream11.odin.dao.query.MysqlQuery.EOL;
import static com.dream11.odin.dao.query.MysqlQuery.GET_ACTION_ID;
import static com.dream11.odin.dao.query.MysqlQuery.GET_COMPONENT_TASKS_AFTER_LAST_UNDEPLOY_FOR_SERVICE_IN_ENV;
import static com.dream11.odin.dao.query.MysqlQuery.GET_COMPONENT_TASKS_FOR_SERVICE_IN_ENV;
import static com.dream11.odin.dao.query.MysqlQuery.GET_ENVIRONMENTS_WITH_ALL_FIELDS;
import static com.dream11.odin.dao.query.MysqlQuery.GET_ENVIRONMENT_TASK;
import static com.dream11.odin.dao.query.MysqlQuery.GET_LAST_COMPONENT_TASKS_FOR_SERVICE_IN_ENV;
import static com.dream11.odin.dao.query.MysqlQuery.GET_LATEST_ENVIRONMENT_TASK;
import static com.dream11.odin.dao.query.MysqlQuery.GET_LATEST_ENVIRONMENT_TASK_EXCLUDING_DELETED;
import static com.dream11.odin.dao.query.MysqlQuery.GET_LATEST_SERVICE_TASKS_FOR_ENV;
import static com.dream11.odin.dao.query.MysqlQuery.IS_ACTIVE_FILTER;
import static com.dream11.odin.dao.query.MysqlQuery.REMOVE_DELETED_ENVIRONMENTS;
import static com.dream11.odin.dao.query.MysqlQuery.UPDATE_ENVIRONMENT;
import static com.dream11.odin.dao.query.MysqlQuery.UPDATE_ENVIRONMENT_ACTIVE_STATUS;
import static com.dream11.odin.dao.query.MysqlQuery.UPDATE_ENVIRONMENT_TASK_STATUS;
import static com.dream11.odin.entity.EnvironmentTask.COL_ACTION_ID;
import static com.dream11.odin.entity.EnvironmentTask.COL_CREATED_BY;
import static com.dream11.odin.entity.EnvironmentTask.COL_ENVID;
import static com.dream11.odin.entity.EnvironmentTask.COL_ID;
import static com.dream11.odin.entity.EnvironmentTask.COL_PROVIDER_ACCOUNT_NAME;
import static com.dream11.odin.entity.EnvironmentTask.COL_RESPONSE;
import static com.dream11.odin.entity.EnvironmentTask.COL_SERVICE_ACCOUNTS_SNAPSHOT;
import static com.dream11.odin.entity.EnvironmentTask.COL_STATUS;
import static com.dream11.odin.entity.EnvironmentTask.COL_VERSION;

import com.dream11.grpc.error.GrpcException;
import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.ApplicationContext;
import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dto.response.ResponseMessage;
import com.dream11.odin.dto.v1.ComponentTask;
import com.dream11.odin.dto.v1.Environment;
import com.dream11.odin.dto.v1.ProviderServiceAccount;
import com.dream11.odin.dto.v1.ServiceTask;
import com.dream11.odin.entity.EnvironmentEntity;
import com.dream11.odin.entity.EnvironmentTask;
import com.dream11.odin.error.OdinError;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountResponse;
import com.dream11.odin.util.DateTimeUtil;
import com.dream11.odin.util.EnvironmentUtil;
import com.dream11.odin.util.JsonUtil;
import com.dream11.odin.util.SingleUtil;
import com.google.inject.Inject;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.functions.Function;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.mysqlclient.MySQLClient;
import io.vertx.reactivex.sqlclient.Row;
import io.vertx.reactivex.sqlclient.RowSet;
import io.vertx.reactivex.sqlclient.SqlConnection;
import io.vertx.reactivex.sqlclient.Tuple;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class EnvironmentDao {
  public static final String CONFIG = "config";
  public static final String VARIABLE_PAIR = "%s-%s";
  final MysqlClient mysqlClient;

  public Single<List<Environment>> getEnvironments(
      String userId, Long orgId, Boolean displayAll, String account, boolean removeDeleted) {
    String query;
    if (account != null && !account.isEmpty()) {
      query = GET_ENVIRONMENTS_WITH_ALL_FIELDS.apply(BY_ACCOUNT + EOL);
    } else if (Boolean.TRUE.equals(displayAll)) {
      query = GET_ENVIRONMENTS_WITH_ALL_FIELDS.apply(ALL);
    } else {
      query = GET_ENVIRONMENTS_WITH_ALL_FIELDS.apply(BY_USER + EOL);
    }
    if (removeDeleted) {
      query = query.replace(EOL, "");
      query = query + REMOVE_DELETED_ENVIRONMENTS + EOL;
    }
    Tuple params;
    if (account != null && !account.isEmpty()) {
      params = Tuple.of(orgId, orgId, account);
    } else if (Boolean.TRUE.equals(displayAll)) {
      params = Tuple.of(orgId, orgId);
    } else {
      params = Tuple.of(orgId, orgId, userId);
    }
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(query)
        .rxExecute(params)
        .map(this::buildEnvironments)
        .compose(SingleUtil.applyDebugLogs(log));
  }

  public Single<Environment> getEnvironmentByNameWithAllFields(Long orgId, String environmentName) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(
            GET_ENVIRONMENTS_WITH_ALL_FIELDS.apply(IS_ACTIVE_FILTER + BY_ENVIRONMENT_NAME + EOL))
        .rxExecute(Tuple.of(orgId, orgId, environmentName))
        .filter(rowSet -> rowSet.size() > 0)
        .switchIfEmpty(
            Single.error(ExceptionUtil.getException(OdinError.ENV_DOES_NOT_EXIST, environmentName)))
        .map(rowSet -> buildEnvironmentBuilder(rowSet).build())
        .compose(SingleUtil.applyDebugLogs(log));
  }

  public Single<List<EnvironmentTask>> getLatestEnvironmentTasks(
      String environmentName, Long orgId) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_LATEST_ENVIRONMENT_TASK_EXCLUDING_DELETED)
        .rxExecute(Tuple.of(environmentName, orgId))
        .filter(rowSet -> rowSet.size() > 0)
        .switchIfEmpty(
            Single.error(ExceptionUtil.getException(OdinError.ENV_DOES_NOT_EXIST, environmentName)))
        .map(
            rowSet ->
                StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(rowSet.iterator(), Spliterator.ORDERED),
                        false)
                    .map(
                        row ->
                            EnvironmentTask.builder()
                                .id(row.getLong(COL_ID))
                                .envId(row.getLong(COL_ENVID))
                                .status(TaskStatus.valueOf(row.getString(COL_STATUS)))
                                .actionId(row.getLong(COL_ACTION_ID))
                                .version(row.getInteger(COL_VERSION))
                                .createdBy(row.getString(COL_CREATED_BY))
                                .serviceAccountSnapshot(
                                    row.getJsonObject(COL_SERVICE_ACCOUNTS_SNAPSHOT).toString())
                                .providerAccountName(row.getString(COL_PROVIDER_ACCOUNT_NAME))
                                .build())
                    .toList())
        .compose(SingleUtil.applyDebugLogs(log));
  }

  public Single<Environment> getEnvironmentById(Long envId) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(ENVIRONMENT_BY_ID)
        .rxExecute(Tuple.of(envId))
        .filter(rowSet -> rowSet.size() > 0)
        .switchIfEmpty(
            Single.error(ExceptionUtil.getException(OdinError.ENV_WITH_ID_DOES_NOT_EXIST, envId)))
        .map(rowset -> buildEnvironmentBuilder(rowset).build())
        .compose(SingleUtil.applyDebugLogs(log));
  }

  public Maybe<Environment> getEnvironmentByNameAndIsActiveIfExists(
      Long orgId, String environmentName) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(
            GET_ENVIRONMENTS_WITH_ALL_FIELDS.apply(IS_ACTIVE_FILTER + BY_ENVIRONMENT_NAME + EOL))
        .rxExecute(Tuple.of(orgId, orgId, environmentName))
        .filter(rowSet -> rowSet.size() > 0)
        .switchIfEmpty(Maybe.empty())
        .flatMap(rowset -> Maybe.just(buildEnvironmentBuilder(rowset).build()));
  }

  public Single<Environment> getEnvironmentWithServices(Long orgId, String environmentName) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_LATEST_ENVIRONMENT_TASK)
        .rxExecute(Tuple.of(environmentName, orgId))
        .filter(rowSet -> rowSet.size() > 0)
        .switchIfEmpty(
            Single.error(ExceptionUtil.getException(OdinError.ENV_DOES_NOT_EXIST, environmentName)))
        .flatMap(
            rowSet ->
                buildEnvironmentWithServices(
                    getEnvironmentServices(rowSet.iterator().next().getLong(COL_ENVID)),
                    buildEnvironmentBuilder(rowSet)))
        .compose(SingleUtil.applyDebugLogs(log));
  }

  private Single<RowSet<Row>> getEnvironmentServices(Long envId) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_LATEST_SERVICE_TASKS_FOR_ENV)
        .rxExecute(Tuple.of(envId))
        .compose(SingleUtil.applyDebugLogs(log));
  }

  private Single<RowSet<Row>> getEnvironmentServiceComponents(Long envId, String serviceName) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_LAST_COMPONENT_TASKS_FOR_SERVICE_IN_ENV)
        .rxExecute(Tuple.of(envId, serviceName))
        .compose(SingleUtil.applyDebugLogs(log));
  }

  public Single<RowSet<Row>> getEnvironmentServiceComponent(
      Long envId, String serviceName, String componentName, boolean filterFailedComponents) {
    String query =
        GET_COMPONENT_TASKS_FOR_SERVICE_IN_ENV.apply(
            String.format(
                GET_COMPONENT_TASKS_AFTER_LAST_UNDEPLOY_FOR_SERVICE_IN_ENV,
                filterFailedComponents ? "AND ct.status != 'FAILED'" : ""));
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(query)
        .rxExecute(Tuple.of(envId, serviceName, componentName, componentName))
        .compose(SingleUtil.applyDebugLogs(log));
  }

  public Single<Environment> getEnvironmentServiceWithAllComponents(
      Long orgId, String environmentName, String serviceName) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_LATEST_ENVIRONMENT_TASK)
        .rxExecute(Tuple.of(environmentName, orgId))
        .filter(rowSet -> rowSet.size() > 0)
        .switchIfEmpty(
            Single.error(ExceptionUtil.getException(OdinError.ENV_DOES_NOT_EXIST, environmentName)))
        .flatMap(
            rowSet ->
                buildEnvironmentServiceWithAllComponents(
                    getEnvironmentServiceComponents(
                        rowSet.iterator().next().getLong(COL_ENVID), serviceName),
                    orgId,
                    environmentName,
                    serviceName))
        .compose(SingleUtil.applyDebugLogs(log));
  }

  public Single<Environment> getEnvironmentServiceWithComponent(
      Long orgId,
      String environmentName,
      String serviceName,
      String componentName,
      boolean filterFailedComponents) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_LATEST_ENVIRONMENT_TASK)
        .rxExecute(Tuple.of(environmentName, orgId))
        .filter(rowSet -> rowSet.size() > 0)
        .switchIfEmpty(
            Single.error(ExceptionUtil.getException(OdinError.ENV_DOES_NOT_EXIST, environmentName)))
        .flatMap(
            rowSet ->
                buildEnvironmentWithServiceWithComponent(
                    getEnvironmentServiceComponent(
                        rowSet.iterator().next().getLong(COL_ENVID),
                        serviceName,
                        componentName,
                        filterFailedComponents),
                    buildEnvironmentBuilder(rowSet),
                    serviceName,
                    componentName))
        .compose(SingleUtil.applyDebugLogs(log));
  }

  public Maybe<List<Optional<EnvironmentTask>>> createEnvAndEnvTask(
      EnvironmentEntity environment, List<GetProviderAccountResponse> providerAccountResponses) {
    return mysqlClient
        .getMasterClient()
        .rxWithTransaction(
            (Function<SqlConnection, Maybe<List<Optional<EnvironmentTask>>>>)
                connection ->
                    createEnvironment(connection, environment)
                        .flatMapMaybe(
                            createdEnvironment ->
                                createEnvironmentTasks(
                                        providerAccountResponses,
                                        createdEnvironment,
                                        Action.CREATE_ENVIRONMENT,
                                        connection)
                                    .toMaybe()))
        .doOnError(err -> log.error("Error while transaction {}", err.getMessage(), err))
        .doOnSuccess(r -> log.debug("Transaction completed"));
  }

  public Single<EnvironmentTask> createEnvironmentTasks(
      SqlConnection sqlConnection, EnvironmentTask environmentTask) {

    Object[] params = {
      environmentTask.envId(),
      environmentTask.actionId(),
      environmentTask.status(),
      environmentTask.version(),
      ApplicationContext.getTraceId(),
      environmentTask.createdBy(),
      environmentTask.providerAccountName(),
      environmentTask.serviceAccountSnapshot(),
      environmentTask.response().orElseGet(() -> "{}"),
      environmentTask.createdBy()
    };
    return sqlConnection
        .preparedQuery(CREATE_ENVIRONMENT_TASK)
        .rxExecute(Tuple.wrap(params))
        .map(
            insertResult -> {
              if (insertResult.rowCount() == 0) {
                log.error("Insert failed for environment task {}", environmentTask);
                throw new GrpcException(OdinError.INTERNAL_SERVER_ERROR);
              }
              return insertResult;
            })
        .map(
            rowSet ->
                EnvironmentTask.builder()
                    .id(rowSet.property(MySQLClient.LAST_INSERTED_ID))
                    .envId(environmentTask.envId())
                    .status(environmentTask.status())
                    .actionId(environmentTask.actionId())
                    .version(environmentTask.version())
                    .createdBy(environmentTask.createdBy())
                    .serviceAccountSnapshot(environmentTask.serviceAccountSnapshot())
                    .providerAccountName(environmentTask.providerAccountName())
                    .response(environmentTask.response())
                    .traceId(environmentTask.traceId())
                    .build());
  }

  public Single<List<Optional<EnvironmentTask>>> createEnvironmentTasks(
      List<GetProviderAccountResponse> providerAccountResponses,
      EnvironmentEntity environmentEntity,
      Action action,
      SqlConnection connection) {
    return Observable.fromIterable(providerAccountResponses)
        .flatMapMaybe(
            providerAccountResponse -> {
              List<String> clusters = extractClusters(providerAccountResponse);
              TaskStatus taskStatus =
                  clusters.isEmpty() ? TaskStatus.SUCCESSFUL : TaskStatus.IN_PROGRESS;

              return getActionId(connection, action)
                  .flatMap(
                      actionId -> {
                        EnvironmentTask environmentTask =
                            EnvironmentTask.builder()
                                .envId(environmentEntity.id())
                                .status(taskStatus)
                                .actionId(actionId)
                                .createdBy(environmentEntity.createdBy())
                                .serviceAccountSnapshot(
                                    JsonFormat.printer().print(providerAccountResponse))
                                .providerAccountName(providerAccountResponse.getAccount().getName())
                                .response(Optional.empty())
                                .version(1)
                                .traceId(Optional.ofNullable(ApplicationContext.getTraceId()))
                                .build();
                        return createEnvironmentTasks(connection, environmentTask);
                      })
                  .map(Optional::of)
                  .toMaybe();
            })
        .toList();
  }

  public Single<List<Optional<EnvironmentTask>>> createEnvironmentTasks(
      List<GetProviderAccountResponse> providerAccountResponses,
      EnvironmentEntity environmentEntity,
      Action action) {
    return mysqlClient
        .getMasterClient()
        .rxGetConnection()
        .flatMap(
            connection ->
                createEnvironmentTasks(
                    providerAccountResponses, environmentEntity, action, connection));
  }

  public Single<EnvironmentEntity> createEnvironment(
      SqlConnection sqlConnection, EnvironmentEntity environment) {
    Object[] params = {
      environment.createdBy(),
      environment.version(),
      environment.orgId(),
      environment.name(),
      environment.createdBy(),
    };
    return sqlConnection
        .preparedQuery(CREATE_ENVIRONMENT)
        .rxExecute(Tuple.wrap(params))
        .map(
            insertResult -> {
              if (insertResult.rowCount() == 0) {
                log.error("Insert failed for environment {}", environment);
                throw new GrpcException(OdinError.INTERNAL_SERVER_ERROR);
              }
              return insertResult;
            })
        .map(
            rowSet ->
                new EnvironmentEntity(
                    rowSet.property(MySQLClient.LAST_INSERTED_ID),
                    environment.orgId(),
                    environment.name(),
                    environment.createdBy(),
                    environment.version()));
  }

  public Completable updateEnvironment(long orgId, Environment environment) {
    Object[] params = {
      environment.getCreatedBy(),
      environment.getOrgId(),
      environment.getName(),
      orgId,
      environment.getId(),
      environment.getVersion()
    };
    return mysqlClient
        .getMasterClient()
        .preparedQuery(UPDATE_ENVIRONMENT)
        .rxExecute(Tuple.wrap(params))
        .map(
            updateResult -> {
              if (updateResult.rowCount() == 0) {
                throw new GrpcException(OdinError.CONFLICTING_UPDATE);
              }
              return updateResult;
            })
        .ignoreElement();
  }

  public Completable updateEnvironmentTaskStatus(ResponseMessage message) {
    Object[] params = {
      message.getStatus(),
      new JsonObject().put("response", message.toString()).toString(),
      message.getId()
    };
    return mysqlClient
        .getMasterClient()
        .preparedQuery(UPDATE_ENVIRONMENT_TASK_STATUS)
        .rxExecute(Tuple.wrap(params))
        .map(
            updateResult -> {
              if (updateResult.rowCount() == 0) {
                log.error("Update failed for environmentTask: {}", message.getId());
                throw new GrpcException(OdinError.INTERNAL_SERVER_ERROR);
              }
              return updateResult;
            })
        .ignoreElement();
  }

  public Completable setEnvironmentInActiveForDeleteEnvironmentTask(long environmentTaskId) {
    Object[] params = {
      environmentTaskId, Action.DELETE_ENVIRONMENT.getName(), TaskStatus.SUCCESSFUL.getValue()
    };
    return mysqlClient
        .getMasterClient()
        .preparedQuery(UPDATE_ENVIRONMENT_ACTIVE_STATUS)
        .rxExecute(Tuple.wrap(params))
        .ignoreElement();
  }

  public Completable updateEnvironmentTaskStatusByIds(
      List<Long> envTaskIds, String reason, String status) {
    JsonObject responseJson = new JsonObject().put("response", reason);
    List<Completable> updateTasks =
        envTaskIds.stream()
            .map(
                envTaskId -> {
                  Object[] params = {status, responseJson.toString(), envTaskId};
                  return mysqlClient
                      .getMasterClient()
                      .preparedQuery(UPDATE_ENVIRONMENT_TASK_STATUS)
                      .rxExecute(Tuple.wrap(params))
                      .flatMapCompletable(
                          updateResult -> {
                            if (updateResult.rowCount() == 0) {
                              log.error("Update failed for environmentTask: {}", envTaskId);
                              return Completable.error(
                                  new GrpcException(OdinError.INTERNAL_SERVER_ERROR));
                            }
                            return Completable.complete();
                          });
                })
            .toList();

    return Completable.mergeDelayError(updateTasks);
  }

  public Single<EnvironmentTask> getEnvironmentTask(Long id) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_ENVIRONMENT_TASK)
        .rxExecute(Tuple.of(id))
        .map(
            rowSet -> {
              if (rowSet.size() == 0) {
                log.error("Environment task with {} not found", id);
                throw ExceptionUtil.getException(OdinError.INTERNAL_SERVER_ERROR);
              }
              Row row = rowSet.iterator().next();
              return EnvironmentTask.builder()
                  .id(row.getLong(COL_ID))
                  .envId(row.getLong(COL_ENVID))
                  .status(TaskStatus.valueOf(row.getString(COL_STATUS)))
                  .actionId(row.getLong(COL_ACTION_ID))
                  .version(row.getInteger(COL_VERSION))
                  .createdBy(row.getString(COL_CREATED_BY))
                  .serviceAccountSnapshot(
                      row.getJsonObject(COL_SERVICE_ACCOUNTS_SNAPSHOT).toString())
                  .providerAccountName(row.getString(COL_PROVIDER_ACCOUNT_NAME))
                  .response(Optional.ofNullable(row.getJsonObject(COL_RESPONSE).toString()))
                  .build();
            })
        .compose(SingleUtil.applyDebugLogs(log));
  }

  public Single<Long> getActionId(SqlConnection sqlConnection, Action action) {
    return sqlConnection
        .preparedQuery(GET_ACTION_ID)
        .rxExecute(Tuple.of(action.name()))
        .filter(rowSet -> rowSet.size() > 0)
        .switchIfEmpty(Single.error(ExceptionUtil.getException(OdinError.INTERNAL_SERVER_ERROR)))
        .map(rowSet -> rowSet.iterator().next().getLong("id"));
  }

  private Single<Environment> buildEnvironmentServiceWithAllComponents(
      Single<RowSet<Row>> rowSet, Long orgId, String environmentName, String serviceName) {
    return rowSet
        .map(
            rows -> {
              if (rows.size() == 0) {
                throw ExceptionUtil.getException(
                    OdinError.SERVICE_DOES_NOT_EXIST_IN_ENV, serviceName, environmentName);
              }
              Row firstRow = rows.iterator().next();
              if (firstRow.getString(SERVICE_NAME) == null) {
                throw ExceptionUtil.getException(
                    OdinError.SERVICE_DOES_NOT_EXIST_IN_ENV, serviceName, environmentName);
              }
              ServiceTask.Builder serviceTaskBuilder = buildServiceTaskBuilder(firstRow);
              List<Single<Environment>> environmentSingles = new ArrayList<>();
              for (Row row : rows) {
                ComponentTask.Builder componentTaskBuilder = buildComponentTaskBuilder(row);
                if (componentTaskBuilder
                    .getStatus()
                    .equalsIgnoreCase(
                        String.format(VARIABLE_PAIR, Action.UNDEPLOY, TaskStatus.SUCCESSFUL))) {
                  continue;
                }
                environmentSingles.add(
                    getEnvironmentServiceWithComponent(
                        orgId,
                        environmentName,
                        serviceName,
                        componentTaskBuilder.getName(),
                        false));
                serviceTaskBuilder.addComponents(componentTaskBuilder);
              }
              AtomicReference<Environment> env = new AtomicReference<>();

              return Flowable.fromIterable(environmentSingles)
                  .flatMapSingle(ev -> ev)
                  .map(
                      ev -> {
                        if (env.get() == null) {
                          env.set(ev);
                        } else {
                          env.set(
                              env.get().toBuilder()
                                  .setServices(
                                      0,
                                      env.get().getServices(0).toBuilder()
                                          .addComponents(ev.getServices(0).getComponents(0))
                                          .build())
                                  .build());
                        }

                        return env.get();
                      })
                  .lastOrError();
            })
        .flatMap(ev -> ev);
  }

  private Single<Environment> buildEnvironmentWithServices(
      Single<RowSet<Row>> rowSet, Environment.Builder environmentBuilder) {
    return rowSet
        .flatMapPublisher(
            rows ->
                Flowable.fromIterable(rows)
                    .filter(row -> row.getString(SERVICE_NAME) != null)
                    .map(this::buildServiceTaskBuilder))
        .collectInto(environmentBuilder, Environment.Builder::addServices)
        .map(Environment.Builder::build);
  }

  private Single<Environment> buildEnvironmentWithServiceWithComponent(
      Single<RowSet<Row>> rowSetSingle,
      Environment.Builder environmentBuilder,
      String serviceName,
      String componentName) {
    AtomicReference<ServiceTask.Builder> serviceTaskBuilder = new AtomicReference<>();
    return rowSetSingle
        .flatMapPublisher(
            rows ->
                Flowable.fromIterable(rows)
                    .filter(row -> row.getString(SERVICE_NAME) != null)
                    .switchIfEmpty(
                        Flowable.error(
                            ExceptionUtil.getException(
                                OdinError.SERVICE_OR_COMPONENT_DOES_NOT_EXIST_IN_ENV,
                                serviceName,
                                componentName,
                                environmentBuilder.getName())))
                    .map(
                        row -> {
                          if (serviceTaskBuilder.get() == null) {
                            serviceTaskBuilder.set(buildServiceTaskBuilder(row));
                          }

                          ComponentTask.Builder componentTaskBuilder =
                              buildComponentTaskBuilder(row);

                          ComponentTask lastComponentTask =
                              serviceTaskBuilder.get().getComponentsCount() > 0
                                  ? serviceTaskBuilder
                                      .get()
                                      .getComponents(
                                          serviceTaskBuilder.get().getComponentsCount() - 1)
                                  : null;
                          if (lastComponentTask != null
                              && lastComponentTask.getName().equals(componentName)) {
                            setOperationConfig(componentTaskBuilder);
                            componentTaskBuilder =
                                mergeComponentTask(
                                    lastComponentTask.toBuilder(), componentTaskBuilder);
                            serviceTaskBuilder
                                .get()
                                .removeComponents(
                                    serviceTaskBuilder.get().getComponentsCount() - 1);
                          } else {
                            componentTaskBuilder = mergeComponentConfigs(componentTaskBuilder);
                          }
                          return serviceTaskBuilder.get().addComponents(componentTaskBuilder);
                        })
                    .lastElement()
                    .toFlowable())
        .collectInto(environmentBuilder, Environment.Builder::addServices)
        .map(Environment.Builder::build);
  }

  private ComponentTask.Builder mergeComponentTask(
      ComponentTask.Builder prevBuilder, ComponentTask.Builder latestBuilder) {

    Struct prevConfig = prevBuilder.getConfig();
    JsonObject prevConfigJson = JsonUtil.getJsonFromProto(prevConfig);

    Struct latestConfig = latestBuilder.getConfig();
    JsonObject latestConfigJson = JsonUtil.getJsonFromProto(latestConfig);

    latestConfigJson = JsonUtil.mergeJsonObjects(prevConfigJson, latestConfigJson);

    ComponentTask.Builder builderCopy = latestBuilder.clone();
    builderCopy.setConfig(JsonUtil.jsonToProtoBuilder(latestConfigJson, Struct.newBuilder()));

    return builderCopy;
  }

  private ComponentTask.Builder mergeComponentConfigs(ComponentTask.Builder componentTaskBuilder) {

    ComponentTask.Builder componentTaskBuilderCopy = componentTaskBuilder.clone();
    Struct config = componentTaskBuilderCopy.getConfig();
    JsonObject configJson = JsonUtil.getJsonFromProto(config);
    configJson = mergeConfigs(configJson);

    componentTaskBuilderCopy.setConfig(
        JsonUtil.jsonToProtoBuilder(configJson, Struct.newBuilder()));
    return componentTaskBuilderCopy;
  }

  private JsonObject mergeConfigs(JsonObject configJson) {
    JsonObject config =
        JsonUtil.mergeJsonObjects(
            configJson.getJsonObject("componentConfig").getJsonObject(CONFIG),
            configJson.getJsonObject("provisioningConfig").getJsonObject("params"));
    return JsonUtil.mergeJsonObjects(config, configJson.getJsonObject("operationConfig"));
  }

  private void setOperationConfig(ComponentTask.Builder componentTaskBuilder) {
    Struct config = componentTaskBuilder.getConfig();
    JsonObject configJson = JsonUtil.getJsonFromProto(config);
    configJson = configJson.getJsonObject("operationConfig", new JsonObject());
    componentTaskBuilder.setConfig(JsonUtil.jsonToProtoBuilder(configJson, Struct.newBuilder()));
  }

  private List<Environment> buildEnvironments(RowSet<Row> rowSet) {
    Map<Long, List<Row>> environmentRowsMap = new HashMap<>();

    for (Row row : rowSet) {
      Long environmentId = row.getLong("id");
      environmentRowsMap.computeIfAbsent(environmentId, k -> new ArrayList<>()).add(row);
    }

    List<Environment> environmentList = new ArrayList<>();

    for (List<Row> environmentRows : environmentRowsMap.values()) {
      Environment.Builder environmentBuilder = buildEnvironmentBuilderFromList(environmentRows);

      environmentList.add(environmentBuilder.build());
    }

    return environmentList;
  }

  private Environment.Builder buildEnvironmentBuilderFromList(List<Row> rowSet) {
    return buildEnvironmentBuilder(rowSet.iterator());
  }

  private ComponentTask.Builder buildComponentTaskBuilder(Row row) {
    JsonObject componentJsonObject =
        new JsonObject()
            .put("name", row.getString("component_name"))
            .put(
                "type",
                row.getJsonObject(CONFIG)
                    .getJsonObject("componentConfig", new JsonObject("{}"))
                    .getValue("type", ""))
            .put(CONFIG, row.getJsonObject(CONFIG))
            .put(
                STATUS,
                String.format(
                    VARIABLE_PAIR,
                    row.getString(COL_ACTION_NAME),
                    row.getString("component_status")));
    return JsonUtil.jsonToProtoBuilder(componentJsonObject, ComponentTask.newBuilder());
  }

  private static final DateTimeFormatter formatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

  private ServiceTask.Builder buildServiceTaskBuilder(Row row) {
    JsonObject jsonObject =
        new JsonObject()
            .put("name", row.getString("service_name"))
            .put("version", row.getString("service_version"))
            .put(CREATED_AT, formatForProtobuf(row.getLocalDateTime(CREATED_AT)))
            .put(UPDATED_AT, formatForProtobuf(row.getLocalDateTime(UPDATED_AT)))
            .put(
                STATUS,
                String.format(
                    VARIABLE_PAIR,
                    row.getString(COL_ACTION_NAME),
                    row.getString("service_status")));
    return JsonUtil.jsonToProtoBuilder(jsonObject, ServiceTask.newBuilder());
  }

  private String formatForProtobuf(LocalDateTime timestamp) {
    // Convert LocalDateTime to a string in UTC with a 'Z' time zone indicator
    return timestamp
        .atZone(ZoneId.systemDefault()) // Convert to ZonedDateTime
        .withZoneSameInstant(ZoneOffset.UTC) // Convert to UTC
        .format(formatter); // Format with the 'Z' suffix
  }

  private Environment.Builder buildEnvironmentBuilder(Iterator<Row> rowIterator) {
    Row firstRow = rowIterator.next();
    JsonObject jsonObject =
        new JsonObject()
            .put("id", firstRow.getLong("env_id"))
            .put("created_by", firstRow.getString("created_by"))
            .put("updated_by", firstRow.getString("updated_by"))
            .put("org_id", firstRow.getLong("org_id"))
            .put("name", firstRow.getString("name"));

    Action action = Action.valueOf(firstRow.getString(COL_ACTION_NAME));
    TaskStatus taskStatus = TaskStatus.valueOf(firstRow.getString(STATUS));

    JsonArray jsonArray = new JsonArray().add(createAccountInformation(firstRow));

    while (rowIterator.hasNext()) {
      Row row = rowIterator.next();
      TaskStatus currStatus = TaskStatus.valueOf(row.getString(STATUS));

      /*
       * If any of the task is failed, then the environment status should be failed, irrespective of other tasks status.
       * If any of the task is in progress, then the environment status should be in progress, provided no tasks have failed.
       * If all the tasks are successful, then the environment status should be successful.
       */
      if (taskStatus.equals(TaskStatus.SUCCESSFUL) && !currStatus.equals(taskStatus)) {
        taskStatus = currStatus;
      }
      jsonArray.add(createAccountInformation(row));
    }

    jsonObject.put("account_information", jsonArray);

    Environment.Builder environmentBuilder =
        JsonUtil.jsonToProtoBuilder(jsonObject, Environment.newBuilder());
    environmentBuilder.setVersion(firstRow.getInteger(EnvironmentEntity.COL_VERSION));
    environmentBuilder.setStatus(EnvironmentUtil.getStatus(action, taskStatus));
    environmentBuilder.setCreatedAt(
        DateTimeUtil.getTimestampFromDateTime(firstRow.getLocalDateTime(CREATED_AT)));
    environmentBuilder.setUpdatedAt(
        DateTimeUtil.getTimestampFromDateTime(firstRow.getLocalDateTime(UPDATED_AT)));
    return environmentBuilder;
  }

  private JsonObject createAccountInformation(Row row) {
    return new JsonObject()
        .put("provider_account_name", row.getString("provider_account_name"))
        .put("service_accounts_snapshot", row.getJsonObject("service_accounts_snapshot"))
        .put(
            STATUS,
            EnvironmentUtil.getStatus(
                Action.valueOf(row.getString(COL_ACTION_NAME)),
                TaskStatus.valueOf(row.getString(STATUS))));
  }

  private Environment.Builder buildEnvironmentBuilder(RowSet<Row> rowSet) {
    return buildEnvironmentBuilder(rowSet.iterator());
  }

  private List<String> extractClusters(GetProviderAccountResponse providerAccountResponse) {
    List<String> clusters = new ArrayList<>();
    for (ProviderServiceAccount providerServiceAccount :
        providerAccountResponse.getAccount().getServicesList()) {
      if (!EKS_PROVIDER_SERVICE_CATEGORY.equals(providerServiceAccount.getCategory())) {
        continue;
      }
      Map<String, Value> fieldMap = providerServiceAccount.getData().getFieldsMap();
      if (fieldMap.containsKey("clusters")) {
        fieldMap
            .get("clusters")
            .getListValue()
            .getValuesList()
            .forEach(
                value ->
                    clusters.add(
                        value.getStructValue().getFieldsMap().get("name").getStringValue()));
      }
    }
    return clusters;
  }
}
