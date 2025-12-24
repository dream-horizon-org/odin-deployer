package com.dream11.odin.dao;

import static com.dream11.odin.error.OdinError.INTERNAL_SERVER_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.dream11.grpc.error.GrpcException;
import com.dream11.odin.ApplicationContext;
import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dao.query.MysqlQuery;
import com.dream11.odin.dto.response.ResponseMessage;
import com.dream11.odin.dto.response.ResponseMessageType;
import com.dream11.odin.entity.EnvironmentEntity;
import com.dream11.odin.entity.EnvironmentTask;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.sqlclient.Row;
import io.vertx.reactivex.sqlclient.RowSet;
import io.vertx.reactivex.sqlclient.SqlConnection;
import io.vertx.reactivex.sqlclient.Tuple;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
class EnvironmentDaoTest {

  EnvironmentDao environmentDao;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  MysqlClient mysqlClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  RowSet<Row> rowSet;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  SqlConnection sqlConnection;

  @BeforeEach
  void setup() {
    environmentDao = new EnvironmentDao(mysqlClient);
  }

  @Test
  void testCreateEnvironmentFailure() {

    // Arrange
    EnvironmentEntity environment =
        EnvironmentEntity.builder().orgId(1).name("test").createdBy("1").build();
    Object[] params = {
      environment.createdBy(),
      environment.version(),
      environment.orgId(),
      environment.name(),
      environment.createdBy()
    };
    Tuple paramsTuple = Tuple.wrap(params);
    try (MockedStatic<Tuple> tuple = Mockito.mockStatic(Tuple.class)) {
      tuple.when(() -> Tuple.wrap(params)).thenReturn(paramsTuple);

      when(this.sqlConnection
              .preparedQuery(MysqlQuery.CREATE_ENVIRONMENT)
              .rxExecute(Tuple.wrap(params)))
          .thenReturn(Single.just(rowSet));

      when(this.rowSet.rowCount()).thenReturn(0);

      // Act and Assert
      environmentDao
          .createEnvironment(sqlConnection, environment)
          .test()
          .assertSubscribed()
          .assertError(
              throwable -> {
                assertTrue(throwable instanceof GrpcException);
                assertEquals("Internal server error", throwable.getMessage());
                assertEquals(
                    INTERNAL_SERVER_ERROR.getErrorCode(),
                    ((GrpcException) throwable).getErrorCode());
                return true;
              });
    }
  }

  @Test
  void testUpdateEnvironmentTaskStatusFailure() {

    // Arrange

    long taskId = 1001;
    String error = "testError";

    Map<String, Object> data = new HashMap<>();
    data.put("data", new HashMap<>().put("message", "data_message"));

    ResponseMessage responseMessage =
        new ResponseMessage(
            taskId, ResponseMessageType.NAMESPACE, TaskStatus.IN_PROGRESS, error, data);

    Object[] params = {
      TaskStatus.IN_PROGRESS,
      new JsonObject().put("response", responseMessage.toString()).toString(),
      taskId
    };
    Tuple paramsTuple = Tuple.wrap(params);
    try (MockedStatic<Tuple> tuple = Mockito.mockStatic(Tuple.class)) {
      tuple.when(() -> Tuple.wrap(params)).thenReturn(paramsTuple);

      when(this.mysqlClient
              .getMasterClient()
              .preparedQuery(MysqlQuery.UPDATE_ENVIRONMENT_TASK_STATUS)
              .rxExecute(Tuple.wrap(params)))
          .thenReturn(Single.just(rowSet));

      when(this.rowSet.rowCount()).thenReturn(0);

      // Act and Assert
      environmentDao
          .updateEnvironmentTaskStatus(
              new ResponseMessage(
                  taskId, ResponseMessageType.NAMESPACE, TaskStatus.IN_PROGRESS, error, data))
          .test()
          .assertSubscribed()
          .assertError(
              throwable -> {
                assertTrue(throwable instanceof GrpcException);
                assertEquals("Internal server error", throwable.getMessage());
                assertEquals(
                    INTERNAL_SERVER_ERROR.getErrorCode(),
                    ((GrpcException) throwable).getErrorCode());
                return true;
              });
    }
  }

  @Test
  void testGetEnvironmentTaskFailure() {
    // Arrange
    long taskId = 1001;
    Tuple idTuple = Tuple.of(taskId);
    try (MockedStatic<Tuple> tuple = Mockito.mockStatic(Tuple.class)) {
      tuple.when(() -> Tuple.of(taskId)).thenReturn(idTuple);

      when(this.mysqlClient
              .getSlaveClient()
              .preparedQuery(MysqlQuery.GET_ENVIRONMENT_TASK)
              .rxExecute(Tuple.of(taskId)))
          .thenReturn(Single.just(rowSet));

      when(this.rowSet.size()).thenReturn(0);

      // Act and Assert
      environmentDao
          .getEnvironmentTask(taskId)
          .test()
          .assertSubscribed()
          .assertError(
              throwable -> {
                assertTrue(throwable instanceof GrpcException);
                assertEquals("Internal server error", throwable.getMessage());
                assertEquals(
                    INTERNAL_SERVER_ERROR.getErrorCode(),
                    ((GrpcException) throwable).getErrorCode());
                return true;
              });
    }
  }

  @Test
  void testCreateEnvironmentTaskFailure() {
    // Arrange
    EnvironmentTask environmentTask =
        EnvironmentTask.builder()
            .envId(1)
            .actionId(1)
            .serviceAccountSnapshot("stag")
            .status(TaskStatus.IN_PROGRESS)
            .createdBy("1")
            .version(1)
            .traceId(Optional.of("test-trace-id"))
            .providerAccountName("stag")
            .response(Optional.of("test"))
            .build();
    Object[] params = {
      environmentTask.envId(),
      environmentTask.actionId(),
      environmentTask.status(),
      environmentTask.version(),
      ApplicationContext.getTraceId(),
      environmentTask.createdBy(),
      environmentTask.providerAccountName(),
      environmentTask.serviceAccountSnapshot(),
      environmentTask.response().orElse("{}"),
      environmentTask.createdBy()
    };

    Tuple paramsTuple = Tuple.wrap(params);
    try (MockedStatic<Tuple> tuple = Mockito.mockStatic(Tuple.class)) {
      tuple.when(() -> Tuple.wrap(params)).thenReturn(paramsTuple);

      when(this.sqlConnection
              .preparedQuery(MysqlQuery.CREATE_ENVIRONMENT_TASK)
              .rxExecute(Tuple.wrap(params)))
          .thenReturn(Single.just(rowSet));

      when(this.rowSet.rowCount()).thenReturn(0);

      // Act and Assert
      environmentDao
          .createEnvironmentTasks(sqlConnection, environmentTask)
          .test()
          .assertSubscribed()
          .assertError(
              throwable -> {
                assertTrue(throwable instanceof GrpcException);
                assertEquals("Internal server error", throwable.getMessage());
                assertEquals(
                    INTERNAL_SERVER_ERROR.getErrorCode(),
                    ((GrpcException) throwable).getErrorCode());
                return true;
              });
    }
  }
}
