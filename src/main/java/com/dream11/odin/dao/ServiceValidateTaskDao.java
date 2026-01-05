package com.dream11.odin.dao;

import static com.dream11.odin.dao.query.MysqlQuery.GET_SERVICE_COMPONENT_VALIDATE_TASK_STATUS;

import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.dto.ServiceComponentValidateTaskStatus;
import com.dream11.odin.util.JsonUtil;
import com.dream11.odin.util.SingleUtil;
import com.google.inject.Inject;
import io.reactivex.Single;
import io.vertx.reactivex.sqlclient.Tuple;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ServiceValidateTaskDao {

  final MysqlClient mysqlClient;

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
