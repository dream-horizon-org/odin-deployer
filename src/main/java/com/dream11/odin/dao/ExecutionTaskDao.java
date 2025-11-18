package com.dream11.odin.dao;

import static com.dream11.odin.dao.query.MysqlQuery.GET_EXECUTION_BY_ID_AND_ACTION;

import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.constant.Action;
import com.dream11.odin.dao.query.MysqlQuery;
import com.dream11.odin.entity.ExecutionTaskEntity;
import com.google.inject.Inject;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.vertx.reactivex.sqlclient.Tuple;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ExecutionTaskDao {

  final MysqlClient mysqlClient;

  public Maybe<List<ExecutionTaskEntity>> getExecutionByIdAndAction(
      String executionId, String action) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(GET_EXECUTION_BY_ID_AND_ACTION)
        .rxExecute(Tuple.of(executionId, action))
        .filter(rows -> rows.size() > 0)
        .map(
            rows ->
                StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(rows.iterator(), Spliterator.ORDERED),
                        false)
                    .<ExecutionTaskEntity>map(
                        row ->
                            ExecutionTaskEntity.builder()
                                .id(row.getLong("id"))
                                .entity(row.getString("entity"))
                                .action(Action.forAction(row.getString("name")))
                                .orgId(row.getLong("org_id"))
                                .status(row.getString("status"))
                                .executionId(row.getString("execution_id"))
                                .payload(row.getJsonObject("payload"))
                                .response(row.getJsonObject("response"))
                                .build())
                    .toList());
  }

  public Completable createExecutionTask(
      String action,
      long orgId,
      String status,
      String entity,
      String executionId,
      String payload,
      String createdBy) {

    Object[] params = {action, orgId, status, entity, executionId, payload, createdBy, createdBy};

    return mysqlClient
        .getMasterClient()
        .preparedQuery(MysqlQuery.INSERT_EXECUTION_TASK)
        .rxExecute(Tuple.wrap(params))
        .ignoreElement();
  }

  public Completable updateExecutionTask(
      String status, String response, String updatedBy, String executionId) {

    Tuple params = Tuple.tuple();
    params.addString(status);
    params.addString(response);
    params.addString(updatedBy);
    params.addString(executionId);

    return mysqlClient
        .getMasterClient()
        .preparedQuery(MysqlQuery.UPDATE_EXECUTION_TASK)
        .rxExecute(params)
        .ignoreElement();
  }
}
