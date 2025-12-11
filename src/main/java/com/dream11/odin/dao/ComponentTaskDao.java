package com.dream11.odin.dao;

import static com.dream11.odin.dao.query.MysqlQuery.CREATE_COMPONENT_TASK;
import static com.dream11.odin.dao.query.MysqlQuery.GET_EXISTING_COMPONENTS_STATUSES_EXCLUDING_HEALTHCHECK_IN_ENV;
import static com.dream11.odin.dao.query.MysqlQuery.GET_EXISTING_COMPONENTS_TASK_IN_ENV;
import static com.dream11.odin.dao.query.MysqlQuery.GET_FAILED_OR_SUCCESS_SERVICE_COMPONENT_TASKS;
import static com.dream11.odin.dao.query.MysqlQuery.GET_LATEST_COMPONENT_TASKS;
import static com.dream11.odin.dao.query.MysqlQuery.GET_LATEST_SUCCESSFUL_DEPLOY_OPERATE_COMPONENT_TASK_IN_ENV;
import static com.dream11.odin.dao.query.MysqlQuery.UPDATE_COMPONENT_TASK_STATUSES;
import static io.vertx.reactivex.mysqlclient.MySQLClient.LAST_INSERTED_ID;

import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dto.ComponentData;
import com.dream11.odin.dto.ComponentDataStatus;
import com.dream11.odin.dto.v1.ComponentDefinition;
import com.dream11.odin.dto.v1.ComponentProvisioningConfig;
import com.dream11.odin.entity.ComponentTaskEntity;
import com.dream11.odin.entity.ServiceTaskEntity;
import com.dream11.odin.util.JsonUtil;
import com.dream11.odin.util.SingleUtil;
import com.google.inject.Inject;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.sqlclient.Row;
import io.vertx.reactivex.sqlclient.SqlConnection;
import io.vertx.reactivex.sqlclient.Tuple;
import java.util.Arrays;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ComponentTaskDao {

  final MysqlClient mysqlClient;

  private static Tuple getTuple(ComponentTaskEntity componentTaskEntity) {
    return Tuple.wrap(
        new Object[] {
          componentTaskEntity.getServiceTaskEntity().getId(),
          componentTaskEntity.getComponentName(),
          componentTaskEntity.getAction().getName(),
          componentTaskEntity.getStatus().getValue(),
          componentTaskEntity.getConfig(),
          componentTaskEntity.getConfigHash(),
          componentTaskEntity.getVersion(),
          componentTaskEntity.getAccounts(),
          componentTaskEntity.getCreatedBy(),
          componentTaskEntity.getUpdatedBy()
        });
  }

  public Single<List<ComponentTaskEntity>> createComponentTasks(
      SqlConnection connection, List<ComponentTaskEntity> componentTaskEntities) {
    List<Single<ComponentTaskEntity>> entitiesList =
        componentTaskEntities.stream()
            .map(componentTaskEntity -> createComponentTask(connection, componentTaskEntity))
            .toList();

    return Single.zip(
        entitiesList, args -> Arrays.stream(args).map(ComponentTaskEntity.class::cast).toList());
  }

  public Single<ComponentTaskEntity> createComponentTask(
      SqlConnection connection, ComponentTaskEntity componentTaskEntity) {
    return connection
        .preparedQuery(CREATE_COMPONENT_TASK)
        .rxExecute(ComponentTaskDao.getTuple(componentTaskEntity))
        .map(result -> componentTaskEntity.withId(result.property(LAST_INSERTED_ID)))
        .doOnSuccess(
            createdComponentTask ->
                log.info(
                    "Component task created successfully, componentTaskId: {}",
                    createdComponentTask.getId()))
        .compose(SingleUtil.applyDebugLogs(log));
  }

  public Single<List<ComponentTaskEntity>> getFailedOrSuccessServiceComponents(
      ServiceTaskEntity serviceTaskEntity) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_FAILED_OR_SUCCESS_SERVICE_COMPONENT_TASKS)
        .rxExecute(Tuple.of(serviceTaskEntity.getId()))
        .map(
            rows ->
                StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(rows.iterator(), Spliterator.ORDERED),
                        false)
                    .map(row -> buildComponentTaskEntity(row, serviceTaskEntity))
                    .toList())
        .compose(SingleUtil.applyDebugLogs(log));
  }

  public Single<ComponentTaskEntity> updateComponentTasks(ComponentTaskEntity componentTaskEntity) {

    Object[] params = {
      componentTaskEntity.getStatus(),
      componentTaskEntity.getResponse(),
      componentTaskEntity.getServiceTaskEntity().getId(),
      componentTaskEntity.getServiceTaskEntity().getId(),
      componentTaskEntity.getComponentName(),
      componentTaskEntity.getAction().getName()
    };

    return mysqlClient
        .getMasterClient()
        .preparedQuery(UPDATE_COMPONENT_TASK_STATUSES)
        .rxExecute(Tuple.wrap(params))
        .map(result -> componentTaskEntity)
        .doOnSuccess(
            componentTaskEntity1 ->
                log.info(
                    "Component task entity {} updated Successfully",
                    componentTaskEntity1.getServiceTaskEntity().getId()))
        .compose(SingleUtil.applyDebugLogs(log));
  }

  private ComponentTaskEntity buildComponentTaskEntity(
      Row row, ServiceTaskEntity serviceTaskEntity) {
    ComponentTaskEntity.ComponentTaskEntityBuilder componentTaskEntity =
        ComponentTaskEntity.builder()
            .serviceTaskEntity(serviceTaskEntity)
            .id(row.getLong("id"))
            .version(1)
            .status(TaskStatus.valueOf(row.getString("status")))
            .config(row.getJsonObject("config"))
            .configHash(row.getString("config_hash"))
            .componentName(row.getString("component_name"))
            .action(Action.valueOf(row.getString("action_name")))
            .accounts(row.getJsonObject("service_account_snapshot"))
            .createdBy(row.getString("created_by"))
            .updatedBy(row.getString("updated_by"));

    if (row.toJson().containsKey("response")) {
      componentTaskEntity.response(row.getJsonObject("response"));
    }

    return componentTaskEntity.build();
  }

  public Single<List<ComponentTaskEntity>> getLatestComponentTasks(
      ServiceTaskEntity serviceTaskEntity) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_LATEST_COMPONENT_TASKS)
        .rxExecute(Tuple.of(serviceTaskEntity.getId()))
        .map(
            rows -> {
              log.info("Fetching component tasks for serviceTaskId {}", serviceTaskEntity.getId());
              return StreamSupport.stream(
                      Spliterators.spliteratorUnknownSize(rows.iterator(), Spliterator.ORDERED),
                      false)
                  .map(row -> buildComponentTaskEntity(row, serviceTaskEntity))
                  .toList();
            })
        .compose(SingleUtil.applyDebugLogs(log));
  }

  public Single<List<ComponentDataStatus>>
      getComponentsDataAndStatusExcludingHealthcheckInServiceAndEnv(
          String serviceName, long envId) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_EXISTING_COMPONENTS_STATUSES_EXCLUDING_HEALTHCHECK_IN_ENV)
        .rxExecute(Tuple.of(serviceName, envId))
        .map(
            rows -> {
              log.info(
                  "Fetching componentDataStatus for env {} and service {}", envId, serviceName);
              return StreamSupport.stream(
                      Spliterators.spliteratorUnknownSize(rows.iterator(), Spliterator.ORDERED),
                      false)
                  .map(
                      row -> {
                        ComponentData componentData =
                            ComponentData.builder()
                                .componentDefinition(
                                    JsonUtil.jsonToProtoBuilder(
                                            row.getJsonObject(Constants.COL_COMPONENT_CONFIG)
                                                .getJsonObject(Constants.COMPONENT_CONFIG_KEY),
                                            ComponentDefinition.newBuilder())
                                        .build())
                                .componentProvisioningConfig(
                                    JsonUtil.jsonToProtoBuilder(
                                            row.getJsonObject(Constants.COL_COMPONENT_CONFIG)
                                                .getJsonObject(Constants.PROVISIONING_CONFIG_KEY),
                                            ComponentProvisioningConfig.newBuilder())
                                        .build())
                                .operationConfigJson(
                                    row.getJsonObject(Constants.COL_COMPONENT_CONFIG)
                                        .getJsonObject(
                                            Constants.OPERATION_CONFIG_KEY, new JsonObject())
                                        .encode())
                                .build();
                        return ComponentDataStatus.builder()
                            .componentData(componentData)
                            .status(TaskStatus.valueOf(row.getString("component_status")))
                            .action(Action.valueOf(row.getString("action_name")))
                            .build();
                      })
                  .toList();
            })
        .compose(SingleUtil.applyDebugLogs(log));
  }

  public Single<List<ComponentTaskEntity>> getComponentTaskEntities(
      String serviceName, Long envId) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_EXISTING_COMPONENTS_TASK_IN_ENV)
        .rxExecute(Tuple.of(serviceName, envId))
        .map(
            rows -> {
              log.info(
                  "Fetching component task entities for env {} and service {}", envId, serviceName);
              return StreamSupport.stream(
                      Spliterators.spliteratorUnknownSize(rows.iterator(), Spliterator.ORDERED),
                      false)
                  .map(
                      row ->
                          buildComponentTaskEntity(
                              row,
                              ServiceTaskEntity.builder()
                                  .id(row.getLong("service_task_id"))
                                  .name(serviceName)
                                  .build()))
                  .toList();
            })
        .compose(SingleUtil.applyDebugLogs(log));
  }

  public Single<ComponentTaskEntity> getLatestSuccessfulDeployOrOperateComponentTask(
      Long envId, String serviceName, String componentName) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_LATEST_SUCCESSFUL_DEPLOY_OPERATE_COMPONENT_TASK_IN_ENV)
        .rxExecute(Tuple.of(envId, serviceName, componentName))
        .map(
            rows -> {
              log.info(
                  "Fetching latest successful component tasks for env {}, service {} and component {}",
                  envId,
                  serviceName,
                  componentName);
              return StreamSupport.stream(
                      Spliterators.spliteratorUnknownSize(rows.iterator(), Spliterator.ORDERED),
                      false)
                  .map(row -> buildComponentTaskEntity(row, null))
                  .findFirst()
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              "No successful deploy or operate task found for component %s in service %s in env %s"
                                  .formatted(componentName, serviceName, envId)));
            })
        .compose(SingleUtil.applyDebugLogs(log));
  }
}
