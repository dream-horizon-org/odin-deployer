package com.dream11.odin.client;

import com.dream11.odin.client.impl.MysqlClientImpl;
import io.vertx.reactivex.core.Vertx;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MysqlClientFactory {

  public MysqlClient getDefaultInstance(Vertx vertx) {
    return new MysqlClientImpl(vertx);
  }
}
