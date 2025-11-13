package com.dream11.odin.client;

import io.reactivex.Completable;
import io.vertx.core.json.JsonObject;

public interface WebClient {
  io.vertx.reactivex.ext.web.client.WebClient getWebClient();

  Completable rxConnect(JsonObject config);

  void close();
}
