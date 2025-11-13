package com.dream11.odin.dao;

import static com.dream11.odin.dao.query.MysqlQuery.CREATE_COMPONENT_VALIDATE_TASK;
import static com.dream11.odin.dao.query.MysqlQuery.UPDATE_COMPONENT_VALIDATE_TASK_STATUS;
import static io.vertx.reactivex.mysqlclient.MySQLClient.LAST_INSERTED_ID;

import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.entity.ComponentValidateTaskEntity;
import com.dream11.odin.util.SingleUtil;
import com.google.inject.Inject;
import io.reactivex.Single;
import io.vertx.reactivex.sqlclient.SqlConnection;
import io.vertx.reactivex.sqlclient.Tuple;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ComponentValidateTaskDao {

  final MysqlClient mysqlClient;

  private static Tuple getTuple(ComponentValidateTaskEntity componentValidateTaskEntity) {
    return Tuple.wrap(
        new Object[] {
          componentValidateTaskEntity.getServiceValidateTaskEntity().getId(),
          componentValidateTaskEntity.getComponentName(),
          componentValidateTaskEntity.getStatus().getValue(),
          componentValidateTaskEntity.getConfig(),
          componentValidateTaskEntity.getConfigHash(),
          componentValidateTaskEntity.getVersion(),
          componentValidateTaskEntity.getCreatedBy(),
          componentValidateTaskEntity.getUpdatedBy()
        });
  }

  public Single<List<ComponentValidateTaskEntity>> createComponentValidateTasks(
      SqlConnection connection, List<ComponentValidateTaskEntity> componentValidateTaskEntities) {
    List<Single<ComponentValidateTaskEntity>> entitiesList =
        componentValidateTaskEntities.stream()
            .map(
                componentValidateTaskEntity ->
                    createComponentValidateTask(connection, componentValidateTaskEntity))
            .toList();

    return Single.zip(
        entitiesList,
        args -> Arrays.stream(args).map(ComponentValidateTaskEntity.class::cast).toList());
  }

  public Single<ComponentValidateTaskEntity> createComponentValidateTask(
      SqlConnection connection, ComponentValidateTaskEntity componentValidateTaskEntity) {
    return connection
        .preparedQuery(CREATE_COMPONENT_VALIDATE_TASK)
        .rxExecute(ComponentValidateTaskDao.getTuple(componentValidateTaskEntity))
        .map(result -> componentValidateTaskEntity.withId(result.property(LAST_INSERTED_ID)))
        .doOnSuccess(
            componentValidateTaskEntity1 ->
                log.info(
                    "Component validate task created successfully, componentTaskId: {}",
                    componentValidateTaskEntity1.getId()))
        .compose(SingleUtil.applyDebugLogs(log));
  }

  public Single<ComponentValidateTaskEntity> updateComponentValidateTask(
      ComponentValidateTaskEntity componentValidateTaskEntity) {

    Object[] params = {
      componentValidateTaskEntity.getStatus(),
      componentValidateTaskEntity.getResponse(),
      componentValidateTaskEntity.getServiceValidateTaskEntity().getId(),
      componentValidateTaskEntity.getComponentName()
    };

    return mysqlClient
        .getMasterClient()
        .preparedQuery(UPDATE_COMPONENT_VALIDATE_TASK_STATUS)
        .rxExecute(Tuple.wrap(params))
        .map(result -> componentValidateTaskEntity)
        .doOnSuccess(
            componentTaskEntity1 ->
                log.info(
                    "Component validate task entity {} updated successfully",
                    componentTaskEntity1.getServiceValidateTaskEntity().getId()))
        .compose(SingleUtil.applyDebugLogs(log));
  }
}
