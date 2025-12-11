package com.dream11.odin.dao;

import static com.dream11.odin.constant.Constants.COMPONENT_DEFINITION;
import static com.dream11.odin.constant.Constants.COMPONENT_NAME;
import static com.dream11.odin.constant.Constants.LEGACY_COMPONENT_NAME;
import static com.dream11.odin.constant.Constants.NAME;
import static com.dream11.odin.constant.Constants.OPERATION_CONFIG_KEY;
import static com.dream11.odin.constant.Constants.OPERATION_NAME;
import static com.dream11.odin.constant.Constants.SERVICE_OPERATION_ADD_COMPONENT;
import static com.dream11.odin.constant.Constants.SERVICE_OPERATION_REMOVE_COMPONENT;
import static com.dream11.odin.dao.query.MysqlQuery.CREATE_SERVICE_TASK;
import static com.dream11.odin.dao.query.MysqlQuery.GET_ALL_RUNNING_SERVICE_NAMES_FROM_ENV;
import static com.dream11.odin.dao.query.MysqlQuery.GET_LATEST_COMPLETED_DEPLOYED_OR_OPERATED_OR_HEALTHCHECK_SERVICE_TASK;
import static com.dream11.odin.dao.query.MysqlQuery.GET_LATEST_COMPLETED_SERVICE_TASK;
import static com.dream11.odin.dao.query.MysqlQuery.GET_LATEST_NON_HEALTHCHECK_SERVICE_TASK;
import static com.dream11.odin.dao.query.MysqlQuery.GET_LATEST_SERVICE_TASK_FROM_ENV_AND_ORG;
import static com.dream11.odin.dao.query.MysqlQuery.GET_SERVICE_ACTION_STATUS_EXCLUDING_HEALTHCHECK;
import static com.dream11.odin.dao.query.MysqlQuery.GET_SERVICE_COMPONENT_TASK_STATUS;
import static com.dream11.odin.dao.query.MysqlQuery.GET_SERVICE_TASK_BY_TRACE_ID_AND_SERVICE_NAME_AND_ENV;
import static com.dream11.odin.dao.query.MysqlQuery.GET_SERVICE_TASK_ID_BY_TRACE_ID;
import static com.dream11.odin.dao.query.MysqlQuery.GET_SERVICE_TASK_STATUS_BY_ID;
import static com.dream11.odin.dao.query.MysqlQuery.UPDATE_SERVICE_TASK_STATUS;
import static io.vertx.reactivex.mysqlclient.MySQLClient.LAST_INSERTED_ID;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.ApplicationContext;
import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dto.ServiceComponentTaskStatus;
import com.dream11.odin.dto.UserDetails;
import com.dream11.odin.entity.ServiceTaskEntity;
import com.dream11.odin.error.OdinError;
import com.dream11.odin.grpc.service.OperateServiceRequest;
import com.dream11.odin.util.JsonUtil;
import com.dream11.odin.util.RxJavaUtil;
import com.dream11.odin.util.ServiceUtil;
import com.dream11.odin.util.SingleUtil;
import com.google.inject.Inject;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.mysqlclient.MySQLException;
import io.vertx.reactivex.sqlclient.Row;
import io.vertx.reactivex.sqlclient.SqlConnection;
import io.vertx.reactivex.sqlclient.Tuple;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ServiceTaskDao {

  final MysqlClient mysqlClient;

  private static final String STATUS = "status";

  public Single<List<ServiceComponentTaskStatus>> getServiceComponentTaskStatusById(
      Long serviceTaskId) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_SERVICE_COMPONENT_TASK_STATUS)
        .rxExecute(Tuple.of(serviceTaskId))
        .map(
            rows ->
                StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(rows.iterator(), Spliterator.ORDERED),
                        false)
                    .map(
                        row ->
                            ServiceComponentTaskStatus.builder()
                                .serviceName(row.getString("service_name"))
                                .serviceVersion(row.getString("service_version"))
                                .serviceTaskId(row.getInteger("service_task_id"))
                                .serviceTaskAction(
                                    Action.forAction(row.getString("service_task_action")))
                                .serviceTaskStatus(
                                    TaskStatus.valueOf(row.getString("service_task_status")))
                                .componentTaskId(row.getInteger("component_task_id"))
                                .componentTaskAction(
                                    Action.forAction(row.getString("component_task_action")))
                                .componentConfig(row.getJsonObject("component_config"))
                                .componentTaskStatus(
                                    TaskStatus.valueOf(row.getString("component_task_status")))
                                .componentTaskResponse(row.getJsonObject("component_task_response"))
                                .componentName(row.getString("component_name"))
                                .build())
                    .toList())
        .compose(SingleUtil.applyDebugLogs(log));
  }

  public Maybe<ServiceTaskEntity> getLatestNonHealthcheckServiceTask(
      Long envId, String serviceName) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_LATEST_NON_HEALTHCHECK_SERVICE_TASK)
        .rxExecute(Tuple.of(envId, serviceName))
        .map(
            rows ->
                StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(rows.iterator(), Spliterator.ORDERED),
                        false)
                    .map(
                        row -> {
                          log.info(
                              "Found service task entity for env {} and service {}",
                              envId,
                              serviceName);
                          return ServiceTaskEntity.builder()
                              .id(row.getLong("id"))
                              .actions(Action.valueOf(row.getString(Constants.COL_ACTIONS)))
                              .config(row.getJsonObject(Constants.COL_CONFIG))
                              .serviceConfigHash(row.getString(Constants.COL_SERVICE_CONFIG_HASH))
                              .envId(envId)
                              .name(row.getString("name"))
                              .serviceVersion(row.getString(Constants.COL_SERVICE_VERSION))
                              .status(TaskStatus.valueOf(row.getString(STATUS)))
                              .createdBy(row.getString(Constants.COL_CREATED_BY))
                              .updatedBy(row.getString(Constants.COL_UPDATED_BY))
                              .version(row.getInteger(Constants.COL_VERSION))
                              .build();
                        })
                    .toList())
        .filter(serviceTaskEntities -> serviceTaskEntities.size() == 1)
        .map(serviceTaskEntities -> serviceTaskEntities.get(0))
        .compose(RxJavaUtil.applyDebugLogs(log));
  }

  public Maybe<ServiceTaskEntity> getServiceTaskByTraceIdServiceNameEnvNameAndAction(
      String traceId, String name, String envName) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_SERVICE_TASK_BY_TRACE_ID_AND_SERVICE_NAME_AND_ENV)
        .rxExecute(Tuple.of(traceId, name, envName))
        .map(
            rows ->
                StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(rows.iterator(), Spliterator.ORDERED),
                        false)
                    .map(
                        row -> {
                          log.info(
                              "found service task entity with trace id {}, serviceName {} , envName {} ",
                              traceId,
                              name,
                              envName);
                          return ServiceTaskEntity.builder()
                              .id(row.getLong("id"))
                              .actions(Action.valueOf(row.getString(Constants.COL_ACTIONS)))
                              .config(row.getJsonObject(Constants.COL_CONFIG))
                              .serviceConfigHash(row.getString(Constants.COL_SERVICE_CONFIG_HASH))
                              .name(row.getString("name"))
                              .serviceVersion(row.getString(Constants.COL_SERVICE_VERSION))
                              .status(TaskStatus.valueOf(row.getString(STATUS)))
                              .createdBy(row.getString(Constants.COL_CREATED_BY))
                              .updatedBy(row.getString(Constants.COL_UPDATED_BY))
                              .version(row.getInteger(Constants.COL_VERSION))
                              .build();
                        })
                    .toList())
        .filter(serviceTaskEntities -> serviceTaskEntities.size() == 1 && !traceId.isEmpty())
        .map(serviceTaskEntities -> serviceTaskEntities.get(0))
        .compose(RxJavaUtil.applyDebugLogs(log));
  }

  public Maybe<ServiceTaskEntity> getLatestCompletedServiceTask(Long envId, String serviceName) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_LATEST_COMPLETED_SERVICE_TASK)
        .rxExecute(Tuple.of(envId, serviceName))
        .map(
            rows ->
                StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(rows.iterator(), Spliterator.ORDERED),
                        false)
                    .map(
                        row -> {
                          log.info(
                              "Found service task entity for env {} and service {}",
                              envId,
                              serviceName);
                          return ServiceTaskEntity.builder()
                              .id(row.getLong("id"))
                              .actions(Action.valueOf(row.getString(Constants.COL_ACTIONS)))
                              .config(row.getJsonObject(Constants.COL_CONFIG))
                              .serviceConfigHash(row.getString(Constants.COL_SERVICE_CONFIG_HASH))
                              .envId(envId)
                              .name(row.getString("name"))
                              .serviceVersion(row.getString(Constants.COL_SERVICE_VERSION))
                              .status(TaskStatus.valueOf(row.getString(STATUS)))
                              .createdBy(row.getString(Constants.COL_CREATED_BY))
                              .updatedBy(row.getString(Constants.COL_UPDATED_BY))
                              .version(row.getInteger(Constants.COL_VERSION))
                              .build();
                        })
                    .toList())
        .filter(serviceTaskEntities -> serviceTaskEntities.size() == 1)
        .map(serviceTaskEntities -> serviceTaskEntities.get(0))
        .compose(RxJavaUtil.applyDebugLogs(log));
  }

  public Maybe<ServiceTaskEntity> getLatestCompletedServiceTask(
      String serviceName, String envName, Long orgId) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_LATEST_SERVICE_TASK_FROM_ENV_AND_ORG)
        .rxExecute(Tuple.of(envName, orgId, serviceName))
        .map(
            rows ->
                StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(rows.iterator(), Spliterator.ORDERED),
                        false)
                    .map(
                        row -> {
                          log.info(
                              "Found service task entity for env {}, service {} and org {}",
                              envName,
                              serviceName,
                              orgId);
                          return ServiceTaskEntity.builder()
                              .id(row.getLong("id"))
                              .actions(Action.valueOf(row.getString(Constants.COL_ACTIONS)))
                              .config(row.getJsonObject(Constants.COL_CONFIG))
                              .serviceConfigHash(row.getString(Constants.COL_SERVICE_CONFIG_HASH))
                              .envId(row.getLong("env_id"))
                              .name(row.getString("name"))
                              .serviceVersion(row.getString(Constants.COL_SERVICE_VERSION))
                              .status(TaskStatus.valueOf(row.getString(STATUS)))
                              .createdBy(row.getString(Constants.COL_CREATED_BY))
                              .updatedBy(row.getString(Constants.COL_UPDATED_BY))
                              .version(row.getInteger(Constants.COL_VERSION))
                              .build();
                        })
                    .toList())
        .filter(serviceTaskEntities -> serviceTaskEntities.size() == 1)
        .map(serviceTaskEntities -> serviceTaskEntities.get(0))
        .compose(RxJavaUtil.applyDebugLogs(log));
  }

  public Single<ServiceTaskEntity> createServiceTask(
      SqlConnection sqlConnection, ServiceTaskEntity serviceTaskEntity) {

    Object[] params = {
      serviceTaskEntity.getActions().getName(),
      serviceTaskEntity.getConfig(),
      serviceTaskEntity.getServiceConfigHash(),
      serviceTaskEntity.getEnvId(),
      serviceTaskEntity.getName(),
      serviceTaskEntity.getServiceVersion(),
      serviceTaskEntity.getStatus().getValue(),
      serviceTaskEntity.getVersion(),
      serviceTaskEntity.getTraceId(),
      serviceTaskEntity.getCreatedBy(),
      serviceTaskEntity.getUpdatedBy()
    };

    return sqlConnection
        .preparedQuery(CREATE_SERVICE_TASK)
        .rxExecute(Tuple.wrap(params))
        .map(result -> serviceTaskEntity.withId(result.property(LAST_INSERTED_ID)))
        .onErrorResumeNext(
            err -> {
              log.error(err.getMessage());
              if (err instanceof MySQLException mySQLException
                  && mySQLException.getErrorCode() == 1062) {
                return Single.error(ExceptionUtil.getException(OdinError.DUPLICATE_SERVICE_ACTION));
              }
              return Single.error(err);
            })
        .doOnSuccess(
            createdServiceTaskEntity ->
                log.info(
                    "Service task created successfully, serviceTaskId: {}",
                    createdServiceTaskEntity.getId()))
        .compose(SingleUtil.applyDebugLogs(log));
  }

  public Single<ServiceTaskEntity> updateServiceTaskByDeploymentId(
      ServiceTaskEntity serviceTaskEntity) {

    Object[] params = {serviceTaskEntity.getStatus(), serviceTaskEntity.getId()};

    return mysqlClient
        .getMasterClient()
        .preparedQuery(UPDATE_SERVICE_TASK_STATUS)
        .rxExecute(Tuple.wrap(params))
        .map(result -> serviceTaskEntity)
        .doOnSuccess(
            createdServiceTaskEntity ->
                log.info(
                    "Service task entity {} updated successfully with status {}",
                    createdServiceTaskEntity.getId(),
                    createdServiceTaskEntity.getStatus()))
        .compose(SingleUtil.applyDebugLogs(log));
  }

  public Single<Pair<Action, TaskStatus>> getServiceStatusExcludingHealthcheck(
      String serviceName, long envId) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_SERVICE_ACTION_STATUS_EXCLUDING_HEALTHCHECK)
        .rxExecute(Tuple.of(serviceName, envId))
        .map(
            rows -> {
              if (rows.iterator().hasNext()) {
                Row row = rows.iterator().next();
                return Pair.of(
                    Action.valueOf(row.getString("action_name")),
                    TaskStatus.valueOf(row.getString(STATUS)));
              } else
                throw ExceptionUtil.getException(
                    OdinError.SERVICE_DOES_NOT_EXIST_IN_ENV, serviceName, envId);
            })
        .compose(SingleUtil.applyDebugLogs(log));
  }

  /**
   * Gets the latest successful service task entity for the given service name and environment id
   * and increments the revision number. - Revision number is used to identify the number of times
   * service has been operated. - If revision number is not found, it adds revision number as 1. -
   * Revision number follows the format `<existing_service_version>-OPERATE.<revision_number>`
   *
   * @param serviceName service name
   * @param envId Environment Id
   * @return ServiceTaskEntity
   */
  public Single<ServiceTaskEntity> createServiceTaskEntityForOperate(
      String serviceName, Long envId, Action action, OperateServiceRequest request) {
    UserDetails userDetails = ApplicationContext.getUserDetails();
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_LATEST_COMPLETED_DEPLOYED_OR_OPERATED_OR_HEALTHCHECK_SERVICE_TASK)
        .rxExecute(Tuple.of(envId, serviceName))
        .map(
            rows ->
                StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(rows.iterator(), Spliterator.ORDERED),
                        false)
                    .map(
                        row ->
                            ServiceTaskEntity.builder()
                                .envId(envId)
                                .name(row.getString("name"))
                                .serviceVersion(
                                    ServiceUtil.getNextServiceRevision(
                                        row.getString("service_version")))
                                .config(
                                    buildServiceConfigWithOperationDetails(
                                        row.getJsonObject("config"), request))
                                .serviceConfigHash(row.getString("service_config_hash"))
                                .status(TaskStatus.IN_PROGRESS)
                                .version(row.getInteger(Constants.COL_VERSION))
                                .actions(action)
                                .traceId(ApplicationContext.getTraceId())
                                .createdBy(userDetails.getUserId())
                                .updatedBy(userDetails.getUserId())
                                .build())
                    .findFirst()
                    .orElseThrow(
                        () -> {
                          log.error(
                              "Service task entity not found for service {} in env {}",
                              serviceName,
                              envId);
                          return ExceptionUtil.getException(OdinError.INTERNAL_SERVER_ERROR);
                        }))
        .compose(SingleUtil.applyDebugLogs(log));
  }

  public Single<List<String>> getLatestServiceTasks(Long envId) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_ALL_RUNNING_SERVICE_NAMES_FROM_ENV)
        .rxExecute(Tuple.of(envId))
        .map(
            rows ->
                StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(rows.iterator(), Spliterator.ORDERED),
                        false)
                    .map(row -> row.getString("name"))
                    .toList())
        .doOnError(err -> log.error("Error while transaction {}", err.getMessage(), err))
        .doOnSuccess(r -> log.debug("Transaction completed"));
  }

  public Maybe<String> getServiceTaskStatus(String serviceTaskId) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_SERVICE_TASK_STATUS_BY_ID)
        .rxExecute(Tuple.of(serviceTaskId))
        .filter(rows -> rows.size() > 0)
        .map(rows -> rows.iterator().next().getString(Constants.STATUS))
        .doOnError(error -> log.error("Error fetching service task status", error))
        .compose(RxJavaUtil.applyDebugLogs(log));
  }

  public Maybe<String> getServiceTaskId(String traceId) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_SERVICE_TASK_ID_BY_TRACE_ID)
        .rxExecute(Tuple.of(traceId))
        .filter(rows -> rows.size() > 0)
        .map(rows -> rows.iterator().next().getLong(Constants.ID).toString())
        .doOnError(error -> log.error("Error fetching service task status", error))
        .compose(RxJavaUtil.applyDebugLogs(log));
  }

  private JsonObject buildServiceConfigWithOperationDetails(
      JsonObject serviceTaskConfig, OperateServiceRequest request) {
    JsonObject operationConfig =
        new JsonObject()
            .put(
                COMPONENT_NAME,
                !StringUtils.isEmpty(request.getComponentName())
                    ? request.getComponentName()
                    : extractComponentNameFromConfig(
                        request.getOperation(),
                        JsonUtil.convertToJsonSorted(request.getConfigJson())))
            .put(OPERATION_NAME, request.getOperation());
    return serviceTaskConfig.put(OPERATION_CONFIG_KEY, operationConfig);
  }

  private String extractComponentNameFromConfig(String operationName, JsonObject config) {
    return switch (operationName) {
      case SERVICE_OPERATION_ADD_COMPONENT -> config
          .getJsonArray(COMPONENT_DEFINITION)
          .getJsonObject(0)
          .getString(NAME);
      case SERVICE_OPERATION_REMOVE_COMPONENT -> config.getString(LEGACY_COMPONENT_NAME);
      default -> "";
    };
  }
}
