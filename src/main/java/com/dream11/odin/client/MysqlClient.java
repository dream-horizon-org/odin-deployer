package com.dream11.odin.client;

import io.reactivex.Completable;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.mysqlclient.MySQLPool;

public interface MysqlClient {

  MySQLPool getMasterClient();

  MySQLPool getSlaveClient();

  Completable rxConnect(JsonObject config);

  Completable rxClose();
}
