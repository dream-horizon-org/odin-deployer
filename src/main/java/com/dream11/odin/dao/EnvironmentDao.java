package com.dream11.odin.dao;

import static com.dream11.odin.constant.Constants.COL_ACCOUNT_DATA;
import static com.dream11.odin.constant.Constants.COL_ACTION;
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
import static com.dream11.odin.dao.query.MysqlQuery.UPDATE_ENVIRONMENT_EXECUTION_TASK;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dao.query.MysqlQuery;
import com.dream11.odin.entity.EnvironmentAccount;
import com.dream11.odin.entity.EnvironmentEntity;
import com.dream11.odin.entity.EnvironmentEntityWithEnvironmentAccounts;
import com.dream11.odin.entity.EnvironmentServiceEntity;
import com.dream11.odin.error.OdinError;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountResponse;
import com.dream11.odin.util.EnvironmentUtil;
import com.google.inject.Inject;
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

  public Completable updateExecutionStatus(
      String executionId, String accountName, TaskStatus status, String responseString) {
    Object[] params = {status, JsonObject.of("response", responseString), executionId, accountName};
    return mysqlClient
        .getMasterClient()
        .preparedQuery(UPDATE_ENVIRONMENT_EXECUTION_TASK)
        .rxExecute(Tuple.of(params))
        .map(
            updateResult -> {
              if (updateResult.rowCount() == 0) {
                log.error("No rows updated for environment execution task: {}", executionId);
                throw ExceptionUtil.getException(
                    OdinError.NO_ROWS_UPDATED,
                    "execution_task",
                    String.join(",", executionId, accountName));
              }
              return updateResult;
            })
        .ignoreElement();
  }

  public Completable setEnvironmentInActiveForDeleteEnvironment(long environmentAccountId) {
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

  public Maybe<EnvironmentEntityWithEnvironmentAccounts> getEnvironmentWithAccountsIfExists(
      long orgId, String environmentName) {
    return this.mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_ACTIVE_ENVIRONMENT_WITH_ACCOUNTS_FOR_ORG.apply(BY_ENVIRONMENT_NAME))
        .rxExecute(Tuple.of(orgId, environmentName))
        .filter(rowSet -> rowSet.size() > 0)
        .map(this::buildEnvironmentWithAccounts);
  }

  public Single<EnvironmentEntityWithEnvironmentAccounts> getEnvironmentWithAccounts(
      long orgId, String environmentName) {
    return this.getEnvironmentWithAccountsIfExists(orgId, environmentName)
        .switchIfEmpty(
            Single.error(
                ExceptionUtil.getException(OdinError.ENV_DOES_NOT_EXIST, environmentName)));
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

  public Completable updateEnvironmentAccountStatus(long id, TaskStatus status) {
    return mysqlClient
        .getMasterClient()
        .preparedQuery(UPDATE_ENVIRONMENT_ACCOUNT_STATUS)
        .rxExecute(Tuple.of(status, id))
        .map(
            updateResult -> {
              if (updateResult.rowCount() == 0) {
                log.error("No rows updated for environment account: {}", id);
                throw ExceptionUtil.getException(
                    OdinError.NO_ROWS_UPDATED, "environment_account", id);
              }
              return updateResult;
            })
        .ignoreElement();
  }

  // TODO convert this into a single MySQL query using IN clause
  public Completable updateEnvironmentAccountStatusByIds(
      List<Long> envAccountIds, TaskStatus status) {
    List<Completable> updateTasks =
        envAccountIds.stream().map(id -> this.updateEnvironmentAccountStatus(id, status)).toList();
    return Completable.mergeDelayError(updateTasks);
  }
}
