package com.dream11.odin.dao;

import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.util.SingleUtil;
import com.google.inject.Inject;
import io.reactivex.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class HealthCheckDao {

  final MysqlClient mysqlClient;

  public Single<String> mysqlHealthCheck() {
    val masterMysqlClient = mysqlClient.getMasterClient();
    return masterMysqlClient
        .query("SELECT 1;")
        .rxExecute()
        .map(rowSet -> "UP")
        .compose(SingleUtil.applyDebugLogs(log));
  }
}
