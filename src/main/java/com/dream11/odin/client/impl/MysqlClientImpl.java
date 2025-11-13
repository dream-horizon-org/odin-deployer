package com.dream11.odin.client.impl;

import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.util.JsonUtil;
import com.google.inject.Inject;
import io.reactivex.Completable;
import io.vertx.core.json.JsonObject;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.mysqlclient.MySQLPool;
import io.vertx.sqlclient.PoolOptions;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class MysqlClientImpl implements MysqlClient {

  final Vertx vertx;
  MySQLPool masterClient;
  MySQLPool slaveClient;
  JsonObject config;

  public Completable rxConnect(JsonObject config) {
    this.config = config;
    this.createMasterSlavePool();
    return Completable.complete();
  }

  @Override
  public Completable rxClose() {
    return this.masterClient.rxClose().andThen(this.slaveClient.rxClose());
  }

  private void createMasterSlavePool() {
    MySQLConnectOptions masterConnectOptions =
        new MySQLConnectOptions(
            JsonUtil.getJsonObjectFromNestedJson(this.config, "master.connectOptions"));

    MySQLConnectOptions slaveConnectOptions =
        new MySQLConnectOptions(
            JsonUtil.getJsonObjectFromNestedJson(this.config, "slave.connectOptions"));

    PoolOptions masterPoolOptions =
        new PoolOptions(JsonUtil.getJsonObjectFromNestedJson(this.config, "master.poolOptions"));

    PoolOptions slavePoolOptions =
        new PoolOptions(JsonUtil.getJsonObjectFromNestedJson(this.config, "slave.poolOptions"));

    this.masterClient = MySQLPool.pool(this.vertx, masterConnectOptions, masterPoolOptions);
    this.slaveClient = MySQLPool.pool(this.vertx, slaveConnectOptions, slavePoolOptions);
  }
}
