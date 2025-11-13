package com.dream11.odin.dao;

import static com.dream11.odin.dao.query.MysqlQuery.CREATE_SERVICE_VALIDATE_TASK;
import static com.dream11.odin.dao.query.MysqlQuery.GET_SERVICE_COMPONENT_VALIDATE_TASK_STATUS;
import static com.dream11.odin.dao.query.MysqlQuery.UPDATE_SERVICE_VALIDATE_TASK_STATUS;
import static io.vertx.reactivex.mysqlclient.MySQLClient.LAST_INSERTED_ID;

import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.dto.ServiceComponentValidateTaskStatus;
import com.dream11.odin.entity.ServiceValidateTaskEntity;
import com.dream11.odin.util.JsonUtil;
import com.dream11.odin.util.SingleUtil;
import com.google.inject.Inject;
import io.reactivex.Single;
import io.vertx.reactivex.sqlclient.SqlConnection;
import io.vertx.reactivex.sqlclient.Tuple;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ServiceValidateTaskDao {

  final MysqlClient mysqlClient;

  public Single<ServiceValidateTaskEntity> createServiceValidateTask(
      SqlConnection sqlConnection, ServiceValidateTaskEntity serviceValidateTaskEntity) {

    Object[] params = {
      serviceValidateTaskEntity.getConfig(),
      serviceValidateTaskEntity.getServiceConfigHash(),
      serviceValidateTaskEntity.getName(),
      serviceValidateTaskEntity.getServiceVersion(),
      serviceValidateTaskEntity.getStatus().getValue(),
      serviceValidateTaskEntity.getVersion(),
      serviceValidateTaskEntity.getTraceId(),
      serviceValidateTaskEntity.getCreatedBy(),
      serviceValidateTaskEntity.getUpdatedBy()
    };

    return sqlConnection
        .preparedQuery(CREATE_SERVICE_VALIDATE_TASK)
        .rxExecute(Tuple.wrap(params))
        .map(result -> serviceValidateTaskEntity.withId(result.property(LAST_INSERTED_ID)))
        .doOnSuccess(
            taskEntity ->
                log.info(
                    "Service validate task created successfully, serviceValidateTaskId: {}",
                    taskEntity.getId()))
        .compose(SingleUtil.applyDebugLogs(log));
  }

  public Single<ServiceValidateTaskEntity> updateServiceValidateTaskByDeploymentId(
      ServiceValidateTaskEntity serviceValidateTaskEntity) {

    return mysqlClient
        .getMasterClient()
        .preparedQuery(UPDATE_SERVICE_VALIDATE_TASK_STATUS)
        .rxExecute(
            Tuple.of(serviceValidateTaskEntity.getStatus(), serviceValidateTaskEntity.getId()))
        .map(result -> serviceValidateTaskEntity)
        .doOnSuccess(
            serviceValidateTaskEntity1 ->
                log.info(
                    "Service validate task entity {} updated Successfully",
                    serviceValidateTaskEntity1.getId()))
        .compose(SingleUtil.applyDebugLogs(log));
  }

  public Single<List<ServiceComponentValidateTaskStatus>> getServiceComponentValidateTaskStatusById(
      Long serviceTaskId) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_SERVICE_COMPONENT_VALIDATE_TASK_STATUS)
        .rxExecute(Tuple.of(serviceTaskId))
        .map(rowSet -> JsonUtil.rowSetToList(rowSet, ServiceComponentValidateTaskStatus.class))
        .compose(SingleUtil.applyDebugLogs(log));
  }
}
