package com.dream11.odin.client;

import com.dream11.odin.client.impl.WebClientImpl;
import io.vertx.reactivex.core.Vertx;
import lombok.experimental.UtilityClass;

@UtilityClass
public class WebClientFactory {
  public WebClient getDefaultInstance(Vertx vertx) {
    return new WebClientImpl(vertx);
  }
}
