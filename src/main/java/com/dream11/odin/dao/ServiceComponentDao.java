package com.dream11.odin.dao;

import static com.dream11.odin.constant.Constants.COL_ACCOUNT_DATA;
import static com.dream11.odin.constant.Constants.COL_COMPONENT_ACTION;
import static com.dream11.odin.constant.Constants.COL_COMPONENT_CONFIG;
import static com.dream11.odin.constant.Constants.COL_COMPONENT_CREATED_AT;
import static com.dream11.odin.constant.Constants.COL_COMPONENT_CREATED_BY;
import static com.dream11.odin.constant.Constants.COL_COMPONENT_NAME;
import static com.dream11.odin.constant.Constants.COL_COMPONENT_STATUS;
import static com.dream11.odin.constant.Constants.COL_COMPONENT_UPDATED_AT;
import static com.dream11.odin.constant.Constants.COL_COMPONENT_UPDATED_BY;
import static com.dream11.odin.constant.Constants.COL_ENVIRONMENT_ID;
import static com.dream11.odin.constant.Constants.COL_SERVICE_ACTION;
import static com.dream11.odin.constant.Constants.COL_SERVICE_CONFIG;
import static com.dream11.odin.constant.Constants.COL_SERVICE_CREATED_AT;
import static com.dream11.odin.constant.Constants.COL_SERVICE_CREATED_BY;
import static com.dream11.odin.constant.Constants.COL_SERVICE_NAME;
import static com.dream11.odin.constant.Constants.COL_SERVICE_STATUS;
import static com.dream11.odin.constant.Constants.COL_SERVICE_UPDATED_AT;
import static com.dream11.odin.constant.Constants.COL_SERVICE_UPDATED_BY;
import static com.dream11.odin.dao.query.MysqlQuery.*;
import static com.dream11.odin.error.OdinError.INTERNAL_SERVER_ERROR;
import static com.dream11.odin.error.OdinError.SERVICE_DOES_NOT_EXIST_IN_ENV;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.entity.ComponentEntity;
import com.dream11.odin.entity.EnvironmentServiceEntity;
import com.dream11.odin.entity.EnvironmentServiceEntityWithComponents;
import com.dream11.odin.error.OdinError;
import com.google.inject.Inject;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.reactivex.sqlclient.Row;
import io.vertx.reactivex.sqlclient.RowSet;
import io.vertx.reactivex.sqlclient.SqlConnection;
import io.vertx.reactivex.sqlclient.Tuple;
import java.util.List;
import java.util.Spliterator;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ServiceComponentDao {
  final MysqlClient mysqlClient;

  public Single<EnvironmentServiceEntityWithComponents> getEnvironmentServiceWithComponents(
      long envId, String serviceName) {
    return this.mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_ENVIRONMENT_SERVICE_COMPONENTS + EOL)
        .rxExecute(Tuple.of(envId, serviceName))
        .filter(rowSet -> rowSet.size() > 0)
        .switchIfEmpty(
            Single.error(
                ExceptionUtil.getException(
                    OdinError.SERVICE_DOES_NOT_EXIST_IN_ENV, serviceName, envId)))
        .map(this::buildEnvironmentServiceEntityWithComponents);
  }

  public Single<EnvironmentServiceEntityWithComponents> getEnvironmentServiceWithComponent(
      long envId, String serviceName, String componentName) {
    return this.mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_ENVIRONMENT_SERVICE_COMPONENT)
        .rxExecute(Tuple.of(envId, serviceName, componentName))
        .filter(rowSet -> rowSet.size() > 0)
        .switchIfEmpty(
            Single.error(
                ExceptionUtil.getException(
                    OdinError.COMPONENT_DOES_NOT_EXIST_IN_SERVICE, componentName, serviceName)))
        .map(this::buildEnvironmentServiceEntityWithComponents);
  }

  // TODO AKSHAY Remove this and use getEnvironmentServiceWithComponents instead
  public Maybe<EnvironmentServiceEntityWithComponents> getServiceComponentStateInEnv(
      long orgId, String envName, String serviceName) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_ENV_SERVICE_COMPONENT)
        .rxExecute(Tuple.of(orgId, envName, serviceName))
        .filter(rowSet -> rowSet.size() > 0)
        .map(this::buildEnvironmentServiceEntityWithComponents);
  }

  public Single<Long> upsertEnvironmentService(
      SqlConnection sqlConnection,
      long environmentId,
      EnvironmentServiceEntity environmentServiceEntity) {

    Object[] params = {
      environmentId,
      environmentServiceEntity.getServiceName(),
      environmentServiceEntity.getServiceAction().getName(),
      environmentServiceEntity.getServiceConfig(),
      environmentServiceEntity.getServiceStatus().name(),
      environmentServiceEntity.getCreatedBy(),
      environmentServiceEntity.getUpdatedBy(),
      environmentServiceEntity.getServiceAction().getName(),
      environmentServiceEntity.getServiceStatus().name()
    };
    return sqlConnection
        .preparedQuery(UPSERT_ENVIRONMENT_SERVICE)
        .rxExecute(Tuple.wrap(params))
        .flatMap(
            __ ->
                getEnvironmentServiceId(
                    sqlConnection, environmentId, environmentServiceEntity.getServiceName()));
  }

  public Completable updateEnvironmentServiceStatus(String status, long id) {
    return mysqlClient
        .getMasterClient()
        .preparedQuery(UPDATE_ENVIRONMENT_SERVICE_STATUS)
        .rxExecute(Tuple.of(status, id))
        .filter(rowSet -> rowSet.rowCount() > 0)
        .switchIfEmpty(Single.error(ExceptionUtil.getException(INTERNAL_SERVER_ERROR)))
        .ignoreElement();
  }

  public Completable updateEnvironmentServiceComponentStatus(
      String status, long serviceId, String componentName) {
    return mysqlClient
        .getMasterClient()
        .preparedQuery(UPDATE_ENVIRONMENT_SERVICE_COMPONENT_STATUS)
        .rxExecute(Tuple.of(status, componentName, serviceId))
        .filter(rowSet -> rowSet.rowCount() > 0)
        .switchIfEmpty(Single.error(ExceptionUtil.getException(INTERNAL_SERVER_ERROR)))
        .ignoreElement();
  }

  public Completable upsertEnvironmentServiceComponents(
      SqlConnection sqlConnection, long serviceId, List<ComponentEntity> componentEntities) {
    return sqlConnection
        .preparedQuery(UPSERT_ENVIRONMENT_SERVICE_COMPONENT)
        .rxExecuteBatch(
            componentEntities.stream()
                .map(
                    c ->
                        Tuple.from(
                            List.of(
                                serviceId,
                                c.getAction().getName(),
                                c.getName(),
                                c.getStatus().getValue(),
                                c.getConfig().encode(),
                                c.getAccountData().encode(),
                                c.getCreatedBy(),
                                c.getUpdatedBy(),
                                c.getAction().getName(),
                                c.getStatus().getValue())))
                .toList())
        .filter(rowSet -> rowSet.rowCount() > 0)
        .switchIfEmpty(
            Single.error(ExceptionUtil.getException(INTERNAL_SERVER_ERROR))) // should never happen
        .ignoreElement();
  }

  Single<Long> getEnvironmentServiceId(
      SqlConnection sqlConnection, long environmentId, String serviceName) {
    return sqlConnection
        .preparedQuery(GET_ENVIRONMENT_SERVICE_ID)
        .rxExecute(Tuple.of(environmentId, serviceName))
        .filter(rowSet -> rowSet.size() > 0)
        .switchIfEmpty(
            Single.error(ExceptionUtil.getException(SERVICE_DOES_NOT_EXIST_IN_ENV, serviceName)))
        .map(rowSet -> rowSet.iterator().next().getLong("id"));
  }

  private EnvironmentServiceEntityWithComponents buildEnvironmentServiceEntityWithComponents(
      RowSet<Row> rowSet) {
    List<ComponentEntity> components =
        StreamSupport.stream(rowSet::spliterator, Spliterator.ORDERED, false)
            .filter(row -> row.getString(COL_COMPONENT_NAME) != null)
            .<ComponentEntity>map(
                row ->
                    ComponentEntity.builder()
                        .name(row.getString(COL_COMPONENT_NAME))
                        .action(Action.valueOf(row.getString(COL_COMPONENT_ACTION)))
                        .status(TaskStatus.valueOf(row.getString(COL_COMPONENT_STATUS)))
                        .config(row.getJsonObject(COL_COMPONENT_CONFIG))
                        .accountData(row.getJsonObject(COL_ACCOUNT_DATA))
                        .createdBy(row.getString(COL_COMPONENT_CREATED_BY))
                        .updatedBy(row.getString(COL_COMPONENT_UPDATED_BY))
                        .createdAt(row.getLocalDateTime(COL_COMPONENT_CREATED_AT))
                        .updatedAt(row.getLocalDateTime(COL_COMPONENT_UPDATED_AT))
                        .build())
            .toList();

    Row firstRow = rowSet.iterator().next();
    return EnvironmentServiceEntityWithComponents.builder()
        .environmentServiceEntity(
            EnvironmentServiceEntity.builder()
                .environmentId(firstRow.getLong(COL_ENVIRONMENT_ID))
                .serviceName(firstRow.getString(COL_SERVICE_NAME))
                .serviceAction(Action.valueOf(firstRow.getString(COL_SERVICE_ACTION)))
                .serviceStatus(TaskStatus.valueOf(firstRow.getString(COL_SERVICE_STATUS)))
                .serviceConfig(firstRow.getJsonObject(COL_SERVICE_CONFIG))
                .createdBy(firstRow.getString(COL_SERVICE_CREATED_BY))
                .updatedBy(firstRow.getString(COL_SERVICE_UPDATED_BY))
                .createdAt(firstRow.getLocalDateTime(COL_SERVICE_CREATED_AT))
                .updatedAt(firstRow.getLocalDateTime(COL_SERVICE_UPDATED_AT))
                .build())
        .components(components)
        .build();
  }
}
