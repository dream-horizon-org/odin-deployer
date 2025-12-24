package com.dream11.odin.dao;

import static com.dream11.odin.constant.Constants.COL_ACCOUNT_DATA;
import static com.dream11.odin.constant.Constants.COL_ACTION;
import static com.dream11.odin.constant.Constants.COL_ACTION_NAME;
import static com.dream11.odin.constant.Constants.COL_CREATED_AT;
import static com.dream11.odin.constant.Constants.COL_CREATED_BY;
import static com.dream11.odin.constant.Constants.COL_ENVIRONMENT_ID;
import static com.dream11.odin.constant.Constants.COL_ID;
import static com.dream11.odin.constant.Constants.COL_NAME;
import static com.dream11.odin.constant.Constants.COL_ORG_ID;
import static com.dream11.odin.constant.Constants.COL_PROVIDER_ACCOUNT_NAME;
import static com.dream11.odin.constant.Constants.COL_SERVICE_ACTION;
import static com.dream11.odin.constant.Constants.COL_SERVICE_CONFIG;
import static com.dream11.odin.constant.Constants.COL_SERVICE_NAME;
import static com.dream11.odin.constant.Constants.COL_SERVICE_STATUS;
import static com.dream11.odin.constant.Constants.COL_STATUS;
import static com.dream11.odin.constant.Constants.COL_UPDATED_AT;
import static com.dream11.odin.constant.Constants.COL_UPDATED_BY;
import static com.dream11.odin.constant.Constants.CREATED_AT;
import static com.dream11.odin.constant.Constants.STATUS;
import static com.dream11.odin.constant.Constants.UPDATED_AT;
import static com.dream11.odin.dao.query.MysqlQuery.BY_ACCOUNT;
import static com.dream11.odin.dao.query.MysqlQuery.BY_ENVIRONMENT_ID;
import static com.dream11.odin.dao.query.MysqlQuery.BY_ENVIRONMENT_NAME;
import static com.dream11.odin.dao.query.MysqlQuery.BY_USER;
import static com.dream11.odin.dao.query.MysqlQuery.CREATE_ENVIRONMENT;
import static com.dream11.odin.dao.query.MysqlQuery.CREATE_ENVIRONMENT_ACCOUNT;
import static com.dream11.odin.dao.query.MysqlQuery.GET_ACTIVE_ENVIRONMENT_WITH_ACCOUNTS_FOR_ORG;
import static com.dream11.odin.dao.query.MysqlQuery.GET_ENVIRONMENT_SERVICES;
import static com.dream11.odin.dao.query.MysqlQuery.GET_ENVIRONMENT_WITH_ACCOUNTS;
import static com.dream11.odin.dao.query.MysqlQuery.UPDATE_ENVIRONMENT_ACCOUNT_STATUS;
import static com.dream11.odin.dao.query.MysqlQuery.UPDATE_ENVIRONMENT_ACTIVE_STATUS;
import static com.dream11.odin.dao.query.MysqlQuery.UPDATE_EXECUTION_TASK;

import com.dream11.grpc.error.GrpcException;
import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dao.query.MysqlQuery;
import com.dream11.odin.dto.response.ResponseMessage;
import com.dream11.odin.dto.v1.AccountInformation;
import com.dream11.odin.dto.v1.ComponentTask;
import com.dream11.odin.dto.v1.Environment;
import com.dream11.odin.dto.v1.ServiceTask;
import com.dream11.odin.entity.EnvironmentAccount;
import com.dream11.odin.entity.EnvironmentEntity;
import com.dream11.odin.entity.EnvironmentEntityWithEnvironmentAccounts;
import com.dream11.odin.entity.EnvironmentServiceEntity;
import com.dream11.odin.error.OdinError;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountResponse;
import com.dream11.odin.util.DateTimeUtil;
import com.dream11.odin.util.EnvironmentUtil;
import com.dream11.odin.util.JsonUtil;
import com.google.inject.Inject;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
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
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class EnvironmentDao {
  public static final String CONFIG = "config";
  public static final String VARIABLE_PAIR = "%s-%s";
  final MysqlClient mysqlClient;

  public Single<List<EnvironmentEntityWithEnvironmentAccounts>> listEnvironments(
      String userId, Long orgId, boolean displayAll, String account) {
    String query = "";
    Tuple params = Tuple.of(orgId);
    if (account != null && !account.isEmpty()) {
      query = query + BY_ACCOUNT;
      params.addString(account);
    }
    if (!displayAll) {
      query = query + BY_USER;
      params.addString(userId);
    }
    return this.mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_ACTIVE_ENVIRONMENT_WITH_ACCOUNTS_FOR_ORG.apply(query))
        .rxExecute(params)
        .map(this::buildEnvironmentsWithAccount);
  }

  private List<EnvironmentEntityWithEnvironmentAccounts> buildEnvironmentsWithAccount(
      RowSet<Row> rowSet) {
    Map<Long, List<Row>> environmentRowsMap = new HashMap<>();

    for (Row row : rowSet) {
      Long environmentId = row.getLong(COL_ENVIRONMENT_ID);
      environmentRowsMap.computeIfAbsent(environmentId, k -> new ArrayList<>()).add(row);
    }
    return environmentRowsMap.values().stream().map(this::buildEnvironmentWithAccounts).toList();
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

  public Single<List<EnvironmentAccount>> createEnvironmentAccounts(
      SqlConnection connection,
      List<GetProviderAccountResponse> providerAccountResponses,
      EnvironmentEntity environmentEntity,
      Action action) {

    return Observable.fromIterable(providerAccountResponses)
        .flatMapSingle(
            providerAccountResponse -> {
              List<String> clusters = EnvironmentUtil.extractClusters(providerAccountResponse);
              TaskStatus taskStatus =
                  clusters.isEmpty() ? TaskStatus.SUCCESSFUL : TaskStatus.IN_PROGRESS;
              EnvironmentAccount environmentAccount =
                  EnvironmentAccount.builder()
                      .environmentId(environmentEntity.id())
                      .status(taskStatus)
                      .action(action)
                      .createdBy(environmentEntity.createdBy())
                      .accountData(
                          new JsonObject(JsonFormat.printer().print(providerAccountResponse)))
                      .accountName(providerAccountResponse.getAccount().getName())
                      .build();

              return this.createEnvironmentAccount(connection, environmentAccount)
                  .map(environmentAccount::withId);
            })
        .toList();
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

  public Completable updateExecutionStatus(ResponseMessage message) {
    // TODO AKSHAY: Update status for each execution task based on message
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

  private Environment.Builder buildEnvironmentBuilder(List<Row> rows) {
    Row firstRow = rows.get(0);
    Action action = Action.valueOf(firstRow.getString(COL_ACTION_NAME));
    TaskStatus taskStatus = TaskStatus.valueOf(firstRow.getString(STATUS));

    List<AccountInformation> accountInformationList = new ArrayList<>();
    for (Row row : rows) {
      TaskStatus currStatus = TaskStatus.valueOf(row.getString(STATUS));
      /*
       * If any of the task is failed, then the environment status should be failed, irrespective of other tasks status.
       * If any of the task is in progress, then the environment status should be in progress, provided no tasks have failed.
       * If all the tasks are successful, then the environment status should be successful.
       */
      if (taskStatus.equals(TaskStatus.SUCCESSFUL) && !currStatus.equals(taskStatus)) {
        taskStatus = currStatus;
      }
      accountInformationList.add(
          AccountInformation.newBuilder()
              .setProviderAccountName(row.getString("account_name"))
              .build());
    }

    return Environment.newBuilder()
        .setId(firstRow.getLong("environment_id"))
        .setName(firstRow.getString("name"))
        .setCreatedBy(firstRow.getString("created_by"))
        .setUpdatedBy(firstRow.getString("updated_by"))
        .setOrgId(firstRow.getLong("org_id"))
        .addAllAccountInformation(accountInformationList)
        .setStatus(EnvironmentUtil.getStatus(action, taskStatus))
        .setCreatedAt(DateTimeUtil.getTimestampFromDateTime(firstRow.getLocalDateTime(CREATED_AT)))
        .setUpdatedAt(DateTimeUtil.getTimestampFromDateTime(firstRow.getLocalDateTime(UPDATED_AT)));
  }

  public Single<EnvironmentEntityWithEnvironmentAccounts> getEnvironmentWithAccounts(
      long orgId, String envName) {
    return this.mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_ACTIVE_ENVIRONMENT_WITH_ACCOUNTS_FOR_ORG.apply(BY_ENVIRONMENT_NAME))
        .rxExecute(Tuple.of(orgId, envName))
        .filter(rowSet -> rowSet.size() > 0)
        .switchIfEmpty(
            Single.error(ExceptionUtil.getException(OdinError.ENV_DOES_NOT_EXIST, envName)))
        .map(this::buildEnvironmentWithAccounts);
  }

  /**
   * Build environment entity along with environment accounts for a single environment
   *
   * @param rows list of rows for a single environment
   * @return EnvironmentEntityWithEnvironmentAccounts
   */
  private EnvironmentEntityWithEnvironmentAccounts buildEnvironmentWithAccounts(List<Row> rows) {
    List<EnvironmentAccount> environmentAccounts =
        rows.stream()
            .map(
                row ->
                    EnvironmentAccount.builder()
                        .id(row.getLong(COL_ID))
                        .environmentId(row.getLong(COL_ENVIRONMENT_ID))
                        .status(TaskStatus.valueOf(row.getString(COL_STATUS)))
                        .action(Action.valueOf(row.getString(COL_ACTION)))
                        .createdBy(row.getString(COL_CREATED_BY))
                        .accountData(row.getJsonObject(COL_ACCOUNT_DATA))
                        .accountName(row.getString(COL_PROVIDER_ACCOUNT_NAME))
                        .build())
            .toList();
    Row firstRow = rows.get(0);
    return EnvironmentEntityWithEnvironmentAccounts.builder()
        .environment(
            EnvironmentEntity.builder()
                .id(firstRow.getLong(COL_ENVIRONMENT_ID))
                .name(firstRow.getString(COL_NAME))
                .createdBy(firstRow.getString(COL_CREATED_BY))
                .createdAt(firstRow.getLocalDateTime(COL_CREATED_AT))
                .updatedBy(firstRow.getString(COL_UPDATED_BY))
                .updatedAt(firstRow.getLocalDateTime(COL_UPDATED_AT))
                .status(
                    EnvironmentUtil.getStatus(
                        Action.valueOf(firstRow.getString(COL_ACTION)),
                        this.getEnvironmentStatus(environmentAccounts)))
                .orgId(firstRow.getLong(COL_ORG_ID))
                .build())
        .environmentAccounts(environmentAccounts)
        .build();
  }

  private EnvironmentEntityWithEnvironmentAccounts buildEnvironmentWithAccounts(
      RowSet<Row> rowSet) {
    return this.buildEnvironmentWithAccounts(
        StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(rowSet.iterator(), Spliterator.ORDERED), false)
            .toList());
  }

  /**
   * Get aggregated status of environment from the environment account statuses
   *
   * @param environmentAccounts
   * @return Aggregated status of the environment
   *     <p>If any of the task is in progress, then the environment status should be in progress. If
   *     any of the task is failed, then the environment status should be failed. f all the tasks
   *     are successful, then the environment status should be successful.
   */
  private TaskStatus getEnvironmentStatus(List<EnvironmentAccount> environmentAccounts) {
    if (environmentAccounts.stream().anyMatch(ea -> ea.status() == TaskStatus.IN_PROGRESS)) {
      return TaskStatus.IN_PROGRESS;
    }

    if (environmentAccounts.stream().anyMatch(ea -> ea.status() == TaskStatus.FAILED)) {
      return TaskStatus.FAILED;
    }

    return TaskStatus.SUCCESSFUL;
  }

  public Completable updateEnvironmentAccounts(
      SqlConnection connection, long envId, Action action, TaskStatus status) {
    return connection
        .preparedQuery(MysqlQuery.UPDATE_ENVIRONMENT_ACCOUNTS)
        .rxExecute(Tuple.of(status, action, envId))
        .ignoreElement();
  }

  public Single<EnvironmentEntityWithEnvironmentAccounts> getEnvironmentById(long envId) {
    return this.mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_ENVIRONMENT_WITH_ACCOUNTS.apply(BY_ENVIRONMENT_ID))
        .rxExecute(Tuple.of(envId))
        .filter(rowSet -> rowSet.size() > 0)
        .switchIfEmpty(
            Single.error(ExceptionUtil.getException(OdinError.ENV_DOES_NOT_EXIST, envId)))
        .map(this::buildEnvironmentWithAccounts);
  }

  public Single<List<EnvironmentServiceEntity>> getEnvironmentServices(long envId) {
    return this.mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_ENVIRONMENT_SERVICES)
        .rxExecute(Tuple.of(envId))
        .map(this::buildEnvironmentServices);
  }

  private List<EnvironmentServiceEntity> buildEnvironmentServices(RowSet<Row> rows) {
    return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(rows.iterator(), Spliterator.ORDERED), false)
        .<EnvironmentServiceEntity>map(
            row ->
                EnvironmentServiceEntity.builder()
                    .environmentId(row.getLong(COL_ENVIRONMENT_ID))
                    .serviceName(row.getString(COL_SERVICE_NAME))
                    .serviceAction(Action.valueOf(row.getString(COL_SERVICE_ACTION)))
                    .serviceStatus(TaskStatus.valueOf(row.getString(COL_SERVICE_STATUS)))
                    .serviceConfig(row.getJsonObject(COL_SERVICE_CONFIG))
                    .createdBy(row.getString(COL_CREATED_BY))
                    .updatedBy(row.getString(COL_UPDATED_BY))
                    .createdAt(row.getLocalDateTime(CREATED_AT))
                    .updatedAt(row.getLocalDateTime(UPDATED_AT))
                    .build())
        .toList();
  }

  // TODO AKSHAY Delete below this
  public Single<Environment> getEnvironmentByNameWithAllFields(Long orgId, String environmentName) {
    return this.mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_ACTIVE_ENVIRONMENT_WITH_ACCOUNTS_FOR_ORG.apply(BY_ENVIRONMENT_NAME))
        .rxExecute(Tuple.of(orgId, orgId, environmentName))
        .filter(rowSet -> rowSet.size() > 0)
        .switchIfEmpty(
            Single.error(ExceptionUtil.getException(OdinError.ENV_DOES_NOT_EXIST, environmentName)))
        .map(rowSet -> buildEnvironmentBuilder(rowSet).build());
  }

  public Maybe<Environment> getEnvironmentByNameAndIsActiveIfExists(
      Long orgId, String environmentName) {
    return this.mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_ACTIVE_ENVIRONMENT_WITH_ACCOUNTS_FOR_ORG.apply(BY_ENVIRONMENT_NAME))
        .rxExecute(Tuple.of(orgId, environmentName))
        .filter(rowSet -> rowSet.size() > 0)
        .flatMap(rowset -> Maybe.just(buildEnvironmentBuilder(rowset).build()));
  }

  //  public Single<Environment> getEnvironmentWithServices(Long orgId, String environmentName) {
  //    return this.mysqlClient
  //        .getSlaveClient()
  //        .preparedQuery(GET_ACTIVE_ENVIRONMENT_WITH_ACCOUNTS_FOR_ORG.apply(BY_ENVIRONMENT_NAME))
  //        .rxExecute(Tuple.of(environmentName, orgId))
  //        .filter(rowSet -> rowSet.size() > 0)
  //        .switchIfEmpty(
  //            Single.error(ExceptionUtil.getException(OdinError.ENV_DOES_NOT_EXIST,
  // environmentName)))
  //        .flatMap(
  //            rowSet ->
  //                buildEnvironmentWithServices(
  //
  // getEnvironmentServices(rowSet.iterator().next().getLong(COL_ENVIRONMENT_ID)),
  //                    buildEnvironmentBuilder(rowSet)))
  //        .compose(SingleUtil.applyDebugLogs(log));
  //  }
  //
  //  private Single<RowSet<Row>> getEnvironmentServices(Long envId) {
  //    return this.mysqlClient
  //        .getSlaveClient()
  //        .preparedQuery(GET_ENVIRONMENT_SERVICES)
  //        .rxExecute(Tuple.of(envId))
  //        .compose(SingleUtil.applyDebugLogs(log));
  //  }

  //  private Single<RowSet<Row>> getEnvironmentServiceComponents(Long envId, String serviceName) {
  //    return this.mysqlClient
  //        .getSlaveClient()
  //        .preparedQuery(GET_LAST_COMPONENT_TASKS_FOR_SERVICE_IN_ENV)
  //        .rxExecute(Tuple.of(envId, serviceName))
  //        .compose(SingleUtil.applyDebugLogs(log));
  //  }
  //
  //  public Single<RowSet<Row>> getEnvironmentServiceComponent(
  //      Long envId, String serviceName, String componentName, boolean filterFailedComponents) {
  //    String query =
  //        GET_COMPONENT_TASKS_FOR_SERVICE_IN_ENV.apply(
  //            String.format(
  //                GET_COMPONENT_TASKS_AFTER_LAST_UNDEPLOY_FOR_SERVICE_IN_ENV,
  //                filterFailedComponents ? "AND ct.status != 'FAILED'" : ""));
  //    return this.mysqlClient
  //        .getSlaveClient()
  //        .preparedQuery(query)
  //        .rxExecute(Tuple.of(envId, serviceName, componentName, componentName))
  //        .compose(SingleUtil.applyDebugLogs(log));
  //  }

  //  public Single<Environment> getEnvironmentServiceWithAllComponents(
  //      Long orgId, String environmentName, String serviceName) {
  //    return this.mysqlClient
  //        .getSlaveClient()
  //        .preparedQuery(GET_ACTIVE_ENVIRONMENT_WITH_ACCOUNTS_FOR_ORG.apply(BY_ENVIRONMENT_NAME))
  //        .rxExecute(Tuple.of(environmentName, orgId))
  //        .filter(rowSet -> rowSet.size() > 0)
  //        .switchIfEmpty(
  //            Single.error(ExceptionUtil.getException(OdinError.ENV_DOES_NOT_EXIST,
  // environmentName)))
  //        .flatMap(
  //            rowSet ->
  //                buildEnvironmentServiceWithAllComponents(
  //                    getEnvironmentServiceComponents(
  //                        rowSet.iterator().next().getLong(COL_ENVIRONMENT_ID), serviceName),
  //                    orgId,
  //                    environmentName,
  //                    serviceName))
  //        .compose(SingleUtil.applyDebugLogs(log));
  //  }
  //
  //  public Single<Environment> getEnvironmentServiceWithComponent(
  //      Long orgId, String environmentName, String serviceName, String componentName) {
  //    return this.getEnvironmentServiceWithComponent(
  //        orgId, environmentName, serviceName, componentName, false);
  //  }
  //
  //  public Single<Environment> getEnvironmentServiceWithComponent(
  //      Long orgId,
  //      String environmentName,
  //      String serviceName,
  //      String componentName,
  //      boolean filterFailedComponents) {
  //    return this.mysqlClient
  //        .getSlaveClient()
  //        .preparedQuery(GET_ACTIVE_ENVIRONMENT_WITH_ACCOUNTS_FOR_ORG.apply(BY_ENVIRONMENT_NAME))
  //        .rxExecute(Tuple.of(environmentName, orgId))
  //        .filter(rowSet -> rowSet.size() > 0)
  //        .switchIfEmpty(
  //            Single.error(ExceptionUtil.getException(OdinError.ENV_DOES_NOT_EXIST,
  // environmentName)))
  //        .flatMap(
  //            rowSet ->
  //                this.buildEnvironmentWithServiceWithComponent(
  //                    this.getEnvironmentServiceComponent(
  //                        rowSet.iterator().next().getLong(COL_ENVIRONMENT_ID),
  //                        serviceName,
  //                        componentName,
  //                        filterFailedComponents),
  //                    buildEnvironmentBuilder(rowSet),
  //                    serviceName,
  //                    componentName))
  //        .compose(SingleUtil.applyDebugLogs(log));
  //  }

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

  public Completable updateEnvironmentAccountStatusByIds(
      List<Long> envAccountIds, TaskStatus status) {
    List<Completable> updateTasks =
        envAccountIds.stream()
            .map(
                id ->
                    mysqlClient
                        .getMasterClient()
                        .preparedQuery(UPDATE_ENVIRONMENT_ACCOUNT_STATUS)
                        .rxExecute(Tuple.of(status, id))
                        .ignoreElement())
            .toList();

    return Completable.mergeDelayError(updateTasks);
  }

  //  private Single<Environment> buildEnvironmentServiceWithAllComponents(
  //      Single<RowSet<Row>> rowSet, Long orgId, String environmentName, String serviceName) {
  //    return rowSet
  //        .map(
  //            rows -> {
  //              if (rows.size() == 0) {
  //                throw ExceptionUtil.getException(
  //                    OdinError.SERVICE_DOES_NOT_EXIST_IN_ENV, serviceName, environmentName);
  //              }
  //              Row firstRow = rows.iterator().next();
  //              if (firstRow.getString(SERVICE_NAME) == null) {
  //                throw ExceptionUtil.getException(
  //                    OdinError.SERVICE_DOES_NOT_EXIST_IN_ENV, serviceName, environmentName);
  //              }
  //              ServiceTask.Builder serviceTaskBuilder = buildServiceTaskBuilder(firstRow);
  //              List<Single<Environment>> environmentSingles = new ArrayList<>();
  //              for (Row row : rows) {
  //                ComponentTask.Builder componentTaskBuilder = buildComponentTaskBuilder(row);
  //                if (componentTaskBuilder
  //                    .getStatus()
  //                    .equalsIgnoreCase(
  //                        String.format(VARIABLE_PAIR, Action.UNDEPLOY, TaskStatus.SUCCESSFUL))) {
  //                  continue;
  //                }
  //                environmentSingles.add(
  //                    getEnvironmentServiceWithComponent(
  //                        orgId,
  //                        environmentName,
  //                        serviceName,
  //                        componentTaskBuilder.getName(),
  //                        false));
  //                serviceTaskBuilder.addComponents(componentTaskBuilder);
  //              }
  //              AtomicReference<Environment> env = new AtomicReference<>();
  //
  //              return Flowable.fromIterable(environmentSingles)
  //                  .flatMapSingle(ev -> ev)
  //                  .map(
  //                      ev -> {
  //                        if (env.get() == null) {
  //                          env.set(ev);
  //                        } else {
  //                          env.set(
  //                              env.get().toBuilder()
  //                                  .setServices(
  //                                      0,
  //                                      env.get().getServices(0).toBuilder()
  //                                          .addComponents(ev.getServices(0).getComponents(0))
  //                                          .build())
  //                                  .build());
  //                        }
  //
  //                        return env.get();
  //                      })
  //                  .lastOrError();
  //            })
  //        .flatMap(ev -> ev);
  //  }
  //
  //  private Single<Environment> buildEnvironmentWithServiceWithComponent(
  //      Single<RowSet<Row>> rowSetSingle,
  //      Environment.Builder environmentBuilder,
  //      String serviceName,
  //      String componentName) {
  //    AtomicReference<ServiceTask.Builder> serviceTaskBuilder = new AtomicReference<>();
  //    return rowSetSingle
  //        .flatMapPublisher(
  //            rows ->
  //                Flowable.fromIterable(rows)
  //                    .filter(row -> row.getString(SERVICE_NAME) != null)
  //                    .switchIfEmpty(
  //                        Flowable.error(
  //                            ExceptionUtil.getException(
  //                                OdinError.SERVICE_OR_COMPONENT_DOES_NOT_EXIST_IN_ENV,
  //                                serviceName,
  //                                componentName,
  //                                environmentBuilder.getName())))
  //                    .map(
  //                        row -> {
  //                          if (serviceTaskBuilder.get() == null) {
  //                            serviceTaskBuilder.set(buildServiceTaskBuilder(row));
  //                          }
  //
  //                          ComponentTask.Builder componentTaskBuilder =
  //                              buildComponentTaskBuilder(row);
  //
  //                          ComponentTask lastComponentTask =
  //                              serviceTaskBuilder.get().getComponentsCount() > 0
  //                                  ? serviceTaskBuilder
  //                                      .get()
  //                                      .getComponents(
  //                                          serviceTaskBuilder.get().getComponentsCount() - 1)
  //                                  : null;
  //                          if (lastComponentTask != null
  //                              && lastComponentTask.getName().equals(componentName)) {
  //                            setOperationConfig(componentTaskBuilder);
  //                            componentTaskBuilder =
  //                                mergeComponentTask(
  //                                    lastComponentTask.toBuilder(), componentTaskBuilder);
  //                            serviceTaskBuilder
  //                                .get()
  //                                .removeComponents(
  //                                    serviceTaskBuilder.get().getComponentsCount() - 1);
  //                          } else {
  //                            componentTaskBuilder = mergeComponentConfigs(componentTaskBuilder);
  //                          }
  //                          return serviceTaskBuilder.get().addComponents(componentTaskBuilder);
  //                        })
  //                    .lastElement()
  //                    .toFlowable())
  //        .collectInto(environmentBuilder, Environment.Builder::addServices)
  //        .map(Environment.Builder::build);
  //  }

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

  private Environment.Builder buildEnvironmentBuilder(RowSet<Row> rowSet) {
    return this.buildEnvironmentBuilder(
        StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(rowSet.iterator(), Spliterator.ORDERED), false)
            .toList());
  }
}
