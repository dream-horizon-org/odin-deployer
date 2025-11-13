package com.dream11.odin.dao;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.dao.query.MysqlQuery;
import io.reactivex.Single;
import io.vertx.reactivex.sqlclient.Row;
import io.vertx.reactivex.sqlclient.RowIterator;
import io.vertx.reactivex.sqlclient.RowSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceTaskDaoTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  MysqlClient mysqlClient;

  @Mock RowSet<Row> rowSet;

  @Mock Row row;

  @InjectMocks ServiceTaskDao serviceTaskDao;

  private static final String SERVICE_TASK_ID = "task123";
  private static final String STATUS_VALUE = "COMPLETED";

  @Test
  void noStatusWhenRecordAbsent() {
    // ServiceTaskId does not exist
    when(rowSet.size()).thenReturn(0);
    // Mock query execution
    when(mysqlClient
            .getSlaveClient()
            .preparedQuery(MysqlQuery.GET_SERVICE_TASK_STATUS_BY_ID)
            .rxExecute(any()))
        .thenReturn(Single.just(rowSet));

    // Invoke method
    serviceTaskDao.getServiceTaskStatus(SERVICE_TASK_ID).test().assertNoValues();
  }

  @Test
  void shouldReturnStatusWhenRecordExists() {
    when(rowSet.size()).thenReturn(1);
    when(row.getString(Constants.STATUS)).thenReturn(STATUS_VALUE);
    // Mock RowIterator
    RowIterator<Row> mockRowIterator = mock(RowIterator.class);
    when(mockRowIterator.next()).thenReturn(row);
    when(rowSet.iterator()).thenReturn(mockRowIterator);
    when(mysqlClient
            .getSlaveClient()
            .preparedQuery(MysqlQuery.GET_SERVICE_TASK_STATUS_BY_ID)
            .rxExecute(any()))
        .thenReturn(Single.just(rowSet));

    // Invoke method
    serviceTaskDao
        .getServiceTaskStatus(SERVICE_TASK_ID)
        .test()
        .assertValue(STATUS_VALUE)
        .assertComplete();
  }

  @Test
  void noServiceTaskIdWhenRecordAbsent() {
    when(rowSet.size()).thenReturn(0);
    // Mock query execution
    when(mysqlClient
            .getSlaveClient()
            .preparedQuery(MysqlQuery.GET_SERVICE_TASK_ID_BY_TRACE_ID)
            .rxExecute(any()))
        .thenReturn(Single.just(rowSet));

    // Invoke method
    serviceTaskDao.getServiceTaskId(SERVICE_TASK_ID).test().assertNoValues();
  }

  @Test
  void shouldReturnServiceTaskIdWhenRecordExists() {
    when(rowSet.size()).thenReturn(1);
    when(row.getLong(Constants.ID)).thenReturn(314L);
    // Mock RowIterator
    RowIterator<Row> mockRowIterator = mock(RowIterator.class);
    when(mockRowIterator.next()).thenReturn(row);
    when(rowSet.iterator()).thenReturn(mockRowIterator);
    when(mysqlClient
            .getSlaveClient()
            .preparedQuery(MysqlQuery.GET_SERVICE_TASK_ID_BY_TRACE_ID)
            .rxExecute(any()))
        .thenReturn(Single.just(rowSet));

    // Invoke method
    serviceTaskDao.getServiceTaskId(SERVICE_TASK_ID).test().assertValue("314").assertComplete();
  }
}
