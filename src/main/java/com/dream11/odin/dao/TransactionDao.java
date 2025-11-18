package com.dream11.odin.dao;

import com.dream11.odin.client.MysqlClient;
import com.google.inject.Inject;
import io.reactivex.Maybe;
import io.reactivex.functions.Function;
import io.vertx.reactivex.sqlclient.SqlConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class TransactionDao {

  final MysqlClient mysqlClient;

  public <T> Maybe<T> executeTransaction(Function<SqlConnection, Maybe<T>> f) {
    return mysqlClient.getMasterClient().rxWithTransaction(f);
  }
}
