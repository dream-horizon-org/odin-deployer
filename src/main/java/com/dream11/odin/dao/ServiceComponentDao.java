package com.dream11.odin.dao;

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

  public Maybe<EnvironmentServiceEntityWithComponents> getServiceComponentStateInEnv(
      long orgId, String envName, String serviceName) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_ENV_SERVICE_COMPONENT)
        .rxExecute(Tuple.of(orgId, envName, serviceName))
        .filter(rowSet -> rowSet.size() > 0)
        .map(this::mapToEnvironmentServiceComponentEntity);
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

  private EnvironmentServiceEntityWithComponents mapToEnvironmentServiceComponentEntity(
      RowSet<Row> rowSet) {
    if (!rowSet.iterator().hasNext()) {
      throw ExceptionUtil.getException(INTERNAL_SERVER_ERROR);
    }

    Row firstRow = rowSet.iterator().next();

    return EnvironmentServiceEntityWithComponents.builder()
        .environmentServiceEntity(
            EnvironmentServiceEntity.builder()
                .environmentName(firstRow.getString("environment_name"))
                .serviceName(firstRow.getString("service_name"))
                .serviceAction(Action.valueOf(firstRow.getString("service_action")))
                .serviceStatus(TaskStatus.valueOf(firstRow.getString("service_status")))
                .serviceConfig(firstRow.getJsonObject("service_config"))
                .createdBy(firstRow.getString("created_by"))
                .updatedBy(firstRow.getString("updated_by"))
                .build())
        .components(
            StreamSupport.stream(rowSet::spliterator, Spliterator.ORDERED, false)
                .<ComponentEntity>map(
                    r ->
                        ComponentEntity.builder()
                            .config(r.getJsonObject("component_config"))
                            .name(r.getString("component_name"))
                            .action(Action.valueOf(r.getString("component_action")))
                            .status(TaskStatus.valueOf(r.getString("component_status")))
                            .build())
                .toList())
        .build();
  }
}
