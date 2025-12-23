package com.dream11.odin.dao;

import static com.dream11.odin.constant.Constants.COL_ACTION_NAME;
import static com.dream11.odin.constant.Constants.COL_CREATED_BY;
import static com.dream11.odin.constant.Constants.CREATED_AT;
import static com.dream11.odin.constant.Constants.KUBERNETES_PROVIDER_SERVICE_CATEGORY;
import static com.dream11.odin.constant.Constants.SERVICE_NAME;
import static com.dream11.odin.constant.Constants.STATUS;
import static com.dream11.odin.constant.Constants.UPDATED_AT;
import static com.dream11.odin.dao.query.MysqlQuery.ALL;
import static com.dream11.odin.dao.query.MysqlQuery.BY_ACCOUNT;
import static com.dream11.odin.dao.query.MysqlQuery.BY_ENVIRONMENT_NAME;
import static com.dream11.odin.dao.query.MysqlQuery.BY_USER;
import static com.dream11.odin.dao.query.MysqlQuery.CREATE_ENVIRONMENT;
import static com.dream11.odin.dao.query.MysqlQuery.CREATE_ENVIRONMENT_ACCOUNT;
import static com.dream11.odin.dao.query.MysqlQuery.ENVIRONMENT_BY_ID;
import static com.dream11.odin.dao.query.MysqlQuery.EOL;
import static com.dream11.odin.dao.query.MysqlQuery.GET_ALL_ENVIRONMENT_ACCOUNTS;
import static com.dream11.odin.dao.query.MysqlQuery.GET_COMPONENT_TASKS_AFTER_LAST_UNDEPLOY_FOR_SERVICE_IN_ENV;
import static com.dream11.odin.dao.query.MysqlQuery.GET_COMPONENT_TASKS_FOR_SERVICE_IN_ENV;
import static com.dream11.odin.dao.query.MysqlQuery.GET_ENVIRONMENTS_WITH_ALL_FIELDS;
import static com.dream11.odin.dao.query.MysqlQuery.GET_ENVIRONMENT_ACCOUNT;
import static com.dream11.odin.dao.query.MysqlQuery.GET_ENVIRONMENT_SERVICES;
import static com.dream11.odin.dao.query.MysqlQuery.GET_ENV_ACCOUNT_ID_FROM_ENV_ID_AND_NAME;
import static com.dream11.odin.dao.query.MysqlQuery.GET_LAST_COMPONENT_TASKS_FOR_SERVICE_IN_ENV;
import static com.dream11.odin.dao.query.MysqlQuery.IS_ACTIVE_FILTER;
import static com.dream11.odin.dao.query.MysqlQuery.REMOVE_DELETED_ENVIRONMENTS;
import static com.dream11.odin.dao.query.MysqlQuery.UPDATE_ENVIRONMENT;
import static com.dream11.odin.dao.query.MysqlQuery.UPDATE_ENVIRONMENT_ACCOUNT;
import static com.dream11.odin.dao.query.MysqlQuery.UPDATE_ENVIRONMENT_ACCOUNT_STATUS;
import static com.dream11.odin.dao.query.MysqlQuery.UPDATE_ENVIRONMENT_ACTIVE_STATUS;
import static com.dream11.odin.dao.query.MysqlQuery.UPDATE_EXECUTION_TASK;
import static com.dream11.odin.entity.EnvironmentAccount.COL_ACCOUNT_DATA;
import static com.dream11.odin.entity.EnvironmentAccount.COL_ACTION;
import static com.dream11.odin.entity.EnvironmentAccount.COL_ENVIRONMENT_ID;
import static com.dream11.odin.entity.EnvironmentAccount.COL_ID;
import static com.dream11.odin.entity.EnvironmentAccount.COL_PROVIDER_ACCOUNT_NAME;
import static com.dream11.odin.entity.EnvironmentAccount.COL_STATUS;

import com.dream11.grpc.error.GrpcException;
import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dto.response.ResponseMessage;
import com.dream11.odin.dto.v1.ComponentTask;
import com.dream11.odin.dto.v1.Environment;
import com.dream11.odin.dto.v1.ServiceTask;
import com.dream11.odin.entity.EnvironmentAccount;
import com.dream11.odin.entity.EnvironmentEntity;
import com.dream11.odin.error.OdinError;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountResponse;
import com.dream11.odin.util.DateTimeUtil;
import com.dream11.odin.util.EnvironmentUtil;
import com.dream11.odin.util.JsonUtil;
import com.dream11.odin.util.SingleUtil;
import com.google.inject.Inject;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.functions.Function;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mysqlclient.MySQLException;
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
  final LockDao lockDao;

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

  public Single<List<EnvironmentAccount>> getEnvironmentAccounts(
      String environmentName, Long orgId) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_ALL_ENVIRONMENT_ACCOUNTS)
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
                            EnvironmentAccount.builder()
                                .id(row.getLong(COL_ID))
                                .environmentId(row.getLong(COL_ENVIRONMENT_ID))
                                .status(TaskStatus.valueOf(row.getString(COL_STATUS)))
                                .action(row.getString(COL_ACTION))
                                .createdBy(row.getString(COL_CREATED_BY))
                                .accountData(row.getJsonObject(COL_ACCOUNT_DATA).toString())
                                .accountName(row.getString(COL_PROVIDER_ACCOUNT_NAME))
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
        .rxExecute(Tuple.of(orgId, environmentName))
        .filter(rowSet -> rowSet.size() > 0)
        .flatMap(rowset -> Maybe.just(buildEnvironmentBuilder(rowset).build()));
  }

  public Single<Environment> getEnvironmentWithServices(Long orgId, String environmentName) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_ALL_ENVIRONMENT_ACCOUNTS)
        .rxExecute(Tuple.of(environmentName, orgId))
        .filter(rowSet -> rowSet.size() > 0)
        .switchIfEmpty(
            Single.error(ExceptionUtil.getException(OdinError.ENV_DOES_NOT_EXIST, environmentName)))
        .flatMap(
            rowSet ->
                buildEnvironmentWithServices(
                    getEnvironmentServices(rowSet.iterator().next().getLong(COL_ENVIRONMENT_ID)),
                    buildEnvironmentBuilder(rowSet)))
        .compose(SingleUtil.applyDebugLogs(log));
  }

  private Single<RowSet<Row>> getEnvironmentServices(Long envId) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_ENVIRONMENT_SERVICES)
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
        .preparedQuery(GET_ALL_ENVIRONMENT_ACCOUNTS)
        .rxExecute(Tuple.of(environmentName, orgId))
        .filter(rowSet -> rowSet.size() > 0)
        .switchIfEmpty(
            Single.error(ExceptionUtil.getException(OdinError.ENV_DOES_NOT_EXIST, environmentName)))
        .flatMap(
            rowSet ->
                buildEnvironmentServiceWithAllComponents(
                    getEnvironmentServiceComponents(
                        rowSet.iterator().next().getLong(COL_ENVIRONMENT_ID), serviceName),
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
        .preparedQuery(GET_ALL_ENVIRONMENT_ACCOUNTS)
        .rxExecute(Tuple.of(environmentName, orgId))
        .filter(rowSet -> rowSet.size() > 0)
        .switchIfEmpty(
            Single.error(ExceptionUtil.getException(OdinError.ENV_DOES_NOT_EXIST, environmentName)))
        .flatMap(
            rowSet ->
                buildEnvironmentWithServiceWithComponent(
                    getEnvironmentServiceComponent(
                        rowSet.iterator().next().getLong(COL_ENVIRONMENT_ID),
                        serviceName,
                        componentName,
                        filterFailedComponents),
                    buildEnvironmentBuilder(rowSet),
                    serviceName,
                    componentName))
        .compose(SingleUtil.applyDebugLogs(log));
  }

  public Single<Long> createEnvironmentAccount(
      SqlConnection sqlConnection, EnvironmentAccount environmentAccount) {
    Object[] params = {
      environmentAccount.environmentId(),
      environmentAccount.action(),
      environmentAccount.status(),
      environmentAccount.createdBy(),
      environmentAccount.accountName(),
      environmentAccount.accountData(),
      environmentAccount.createdBy()
    };
    return sqlConnection
        .preparedQuery(CREATE_ENVIRONMENT_ACCOUNT)
        .rxExecute(Tuple.wrap(params))
        .map(result -> result.property(MySQLClient.LAST_INSERTED_ID));
  }

  public Single<EnvironmentAccount> updateEnvironmentAccount(
      SqlConnection sqlConnection, EnvironmentAccount environmentAccount) {

    Object[] params = {
      environmentAccount.status(),
      environmentAccount.action(),
      environmentAccount.createdBy(),
      environmentAccount.environmentId(),
      environmentAccount.accountName(),
    };

    return sqlConnection
        .preparedQuery(UPDATE_ENVIRONMENT_ACCOUNT)
        .rxExecute(Tuple.wrap(params))
        .flatMap(
            rowSet -> {
              if (rowSet.rowCount() == 0) {
                log.error("No rows updated for environment account {}", environmentAccount);
                throw new GrpcException(OdinError.INTERNAL_SERVER_ERROR);
              }
              return sqlConnection
                  .preparedQuery(GET_ENV_ACCOUNT_ID_FROM_ENV_ID_AND_NAME)
                  .rxExecute(
                      Tuple.of(
                          environmentAccount.environmentId(), environmentAccount.accountName()))
                  .map(
                      result -> {
                        if (!result.iterator().hasNext()) {
                          log.error(
                              "Unable to fetch id for updated environment account {}",
                              environmentAccount);
                          throw new GrpcException(OdinError.INTERNAL_SERVER_ERROR);
                        }

                        Row row = result.iterator().next();
                        Long id = row.getLong("id");

                        return EnvironmentAccount.builder()
                            .id(id)
                            .environmentId(environmentAccount.environmentId())
                            .status(environmentAccount.status())
                            .action(environmentAccount.action())
                            .createdBy(environmentAccount.createdBy())
                            .accountData(environmentAccount.accountData())
                            .accountName(environmentAccount.accountName())
                            .build();
                      });
            });
  }

  public Single<List<EnvironmentAccount>> createEnvironmentAccounts(
      SqlConnection connection,
      List<GetProviderAccountResponse> providerAccountResponses,
      EnvironmentEntity environmentEntity,
      Action action) {

    return Observable.fromIterable(providerAccountResponses)
        .flatMapSingle(
            providerAccountResponse -> {
              List<String> clusters = this.extractClusters(providerAccountResponse);
              TaskStatus taskStatus =
                  clusters.isEmpty() ? TaskStatus.SUCCESSFUL : TaskStatus.IN_PROGRESS;
              EnvironmentAccount environmentAccount =
                  EnvironmentAccount.builder()
                      .environmentId(environmentEntity.id())
                      .status(taskStatus)
                      .action(action.getName())
                      .createdBy(environmentEntity.createdBy())
                      .accountData(JsonFormat.printer().print(providerAccountResponse))
                      .accountName(providerAccountResponse.getAccount().getName())
                      .build();

              return this.createEnvironmentAccount(connection, environmentAccount)
                  .map(environmentAccount::withId);
            })
        .toList();
  }

  public Single<List<Optional<EnvironmentAccount>>> updateEnvironmentAccount(
      List<GetProviderAccountResponse> providerAccountResponses,
      EnvironmentEntity environmentEntity,
      Action action,
      SqlConnection connection) {

    return Observable.fromIterable(providerAccountResponses)
        .flatMapMaybe(
            providerAccountResponse -> {
              EnvironmentAccount environmentAccount =
                  EnvironmentAccount.builder()
                      .environmentId(environmentEntity.id())
                      .status(TaskStatus.IN_PROGRESS)
                      .action(action.getName())
                      .createdBy(environmentEntity.createdBy())
                      .accountData(JsonFormat.printer().print(providerAccountResponse))
                      .accountName(providerAccountResponse.getAccount().getName())
                      .build();

              return updateEnvironmentAccount(connection, environmentAccount)
                  .map(Optional::of)
                  .toMaybe();
            })
        .toList();
  }

  public Single<List<EnvironmentAccount>> filterAndUpdateEnvironmentAccounts(
      List<Optional<EnvironmentAccount>> envAccs,
      List<GetProviderAccountResponse> providerAccountResponses,
      String updatedBy) {

    return Observable.fromIterable(envAccs)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .flatMapMaybe(
            envAccount -> {
              // match provider snapshot by accountName
              GetProviderAccountResponse providerResp =
                  providerAccountResponses.stream()
                      .filter(r -> r.getAccount().getName().equals(envAccount.accountName()))
                      .findFirst()
                      .orElse(null);

              // be defensive if providerResp is null
              List<String> clusters =
                  (providerResp != null)
                      ? extractClusters(providerResp)
                      : java.util.Collections.emptyList();

              if (clusters == null || clusters.isEmpty()) {
                // No clusters: just mark SUCCESSFUL and DO NOT emit this account
                return updateEnvironmentAccountStatusByIds(
                        java.util.Collections.singletonList(envAccount.id()),
                        TaskStatus.SUCCESSFUL.getValue())
                    .andThen(Maybe.empty());
              } else {
                // Has clusters: keep it so the caller can push to SQS
                return Maybe.just(envAccount);
              }
            })
        .toList(); // Single<List<EnvironmentAccount>>
  }

  public Maybe<List<Optional<EnvironmentAccount>>> updateEnvironmentAccount(
      List<GetProviderAccountResponse> providerAccountResponses,
      EnvironmentEntity environmentEntity,
      Action action) {

    return mysqlClient
        .getMasterClient()
        .rxWithTransaction(
            (Function<SqlConnection, Maybe<List<Optional<EnvironmentAccount>>>>)
                connection ->
                    lockDao
                        .ensureEnvironmentLock(
                            connection, environmentEntity.id(), environmentEntity.createdBy())
                        .andThen(
                            lockDao.acquireEnvironmentExclusiveLock(
                                connection, environmentEntity.id()))
                        .andThen(
                            updateEnvironmentAccount(
                                    providerAccountResponses, environmentEntity, action, connection)
                                .toMaybe()))
        .doOnError(
            err -> log.error("Error while updating environment account: {}", err.getMessage(), err))
        .doOnSuccess(r -> log.debug("Environment account update transaction completed"));
  }

  public Single<Long> createEnvironment(
      SqlConnection sqlConnection, EnvironmentEntity environment) {
    return sqlConnection
        .preparedQuery(CREATE_ENVIRONMENT)
        .rxExecute(
            Tuple.of(
                environment.createdBy(),
                environment.orgId(),
                environment.name(),
                environment.createdBy()))
        .map(result -> result.property(MySQLClient.LAST_INSERTED_ID))
        .onErrorResumeNext(
            err -> {
              if (err instanceof MySQLException mySQLException
                  && mySQLException.getErrorCode() == 1062) {
                return Single.error(
                    ExceptionUtil.getException(OdinError.ENV_ALREADY_EXISTS, environment.name()));
              }
              return Single.error(err);
            });
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

  public Completable updateExecutionStatus(ResponseMessage message) {
    // TODO: Update status for each execution task based on message
    Object[] params = {
      message.getStatus(),
      new JsonObject().put("response", message.toString()).toString(),
      message.getExecutionId(),
    };
    return mysqlClient
        .getMasterClient()
        .preparedQuery(UPDATE_EXECUTION_TASK)
        .rxExecute(Tuple.wrap(params))
        .ignoreElement();
  }

  public Completable updateEnvironmentAccountStatus(ResponseMessage message) {
    return mysqlClient
        .getMasterClient()
        .preparedQuery(UPDATE_ENVIRONMENT_ACCOUNT_STATUS)
        .rxExecute(Tuple.of(message.getStatus(), message.getId()))
        .map(
            updateResult -> {
              if (updateResult.rowCount() == 0) {
                log.error("Update failed for environment account: {}", message.getId());
                throw new GrpcException(OdinError.INTERNAL_SERVER_ERROR);
              }
              return updateResult;
            })
        .ignoreElement();
  }

  public Completable setEnvironmentInActiveForDeleteEnvironmentTask(long environmentAccountId) {
    return mysqlClient
        .getMasterClient()
        .preparedQuery(UPDATE_ENVIRONMENT_ACTIVE_STATUS)
        .rxExecute(
            Tuple.of(
                environmentAccountId,
                Action.DELETE_ENVIRONMENT.getName(),
                TaskStatus.SUCCESSFUL.getValue()))
        .ignoreElement();
  }

  public Completable updateEnvironmentAccountStatusByIds(List<Long> envTaskIds, String status) {
    List<Completable> updateTasks =
        envTaskIds.stream()
            .map(
                envTaskId -> {
                  Object[] params = {status, envTaskId};
                  return mysqlClient
                      .getMasterClient()
                      .preparedQuery(UPDATE_ENVIRONMENT_ACCOUNT_STATUS)
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

  public Single<EnvironmentAccount> getEnvironmentAccount(Long id) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_ENVIRONMENT_ACCOUNT)
        .rxExecute(Tuple.of(id))
        .map(
            rowSet -> {
              if (rowSet.size() == 0) {
                log.error("Environment account with {} not found", id);
                throw ExceptionUtil.getException(OdinError.INTERNAL_SERVER_ERROR);
              }
              Row row = rowSet.iterator().next();
              return EnvironmentAccount.builder()
                  .id(row.getLong(COL_ID))
                  .environmentId(row.getLong(COL_ENVIRONMENT_ID))
                  .status(TaskStatus.valueOf(row.getString(COL_STATUS)))
                  .action(row.getString(COL_ACTION))
                  .createdBy(row.getString(COL_CREATED_BY))
                  .accountData(row.getJsonObject(COL_ACCOUNT_DATA).toString())
                  .accountName(row.getString(COL_PROVIDER_ACCOUNT_NAME))
                  .build();
            })
        .compose(SingleUtil.applyDebugLogs(log));
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
            .put("id", firstRow.getLong("environment_id"))
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
    environmentBuilder.setStatus(EnvironmentUtil.getStatus(action, taskStatus));
    environmentBuilder.setCreatedAt(
        DateTimeUtil.getTimestampFromDateTime(firstRow.getLocalDateTime(CREATED_AT)));
    environmentBuilder.setUpdatedAt(
        DateTimeUtil.getTimestampFromDateTime(firstRow.getLocalDateTime(UPDATED_AT)));
    return environmentBuilder;
  }

  private JsonObject createAccountInformation(Row row) {
    return new JsonObject()
        .put("account_name", row.getString("account_name"))
        .put("account_data", row.getJsonObject("account_data"))
        .put(
            STATUS,
            EnvironmentUtil.getStatus(
                Action.valueOf(row.getString(COL_ACTION_NAME)),
                TaskStatus.valueOf(row.getString(STATUS))));
  }

  private Environment.Builder buildEnvironmentBuilder(RowSet<Row> rowSet) {
    return buildEnvironmentBuilder(rowSet.iterator());
  }

  public List<String> extractClusters(GetProviderAccountResponse providerAccountResponse) {
    return providerAccountResponse.getAccount().getServicesList().stream()
        .filter(
            psa ->
                KUBERNETES_PROVIDER_SERVICE_CATEGORY.equals(psa.getCategory())
                    && psa.getData().getFieldsMap().containsKey("clusters"))
        .flatMap(
            psa ->
                psa
                    .getData()
                    .getFieldsMap()
                    .get("clusters")
                    .getListValue()
                    .getValuesList()
                    .stream())
        .map(value -> value.getStructValue().getFieldsMap().get("name").getStringValue())
        .toList();
  }
}
