package com.dream11.odin.client.impl;

import com.google.inject.Inject;
import io.reactivex.Completable;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class WebClientImpl implements com.dream11.odin.client.WebClient {
  final Vertx vertx;
  JsonObject config;
  WebClient webClient;

  public Completable rxConnect(JsonObject config) {
    this.config = config;
    this.createWebClient();
    return Completable.complete();
  }

  @Override
  public void close() {
    if (this.webClient != null) {
      this.webClient.close();
    }
  }

  private void createWebClient() {
    this.webClient = WebClient.create(this.vertx, new WebClientOptions(this.config));
  }
}
