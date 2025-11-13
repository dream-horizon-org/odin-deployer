package com.dream11.odin.setup;

import io.grpc.ManagedChannel;
import io.vertx.core.Vertx;
import io.vertx.grpc.VertxChannelBuilder;

public class TestChannelProvider {
  public static ManagedChannel createChannel(Vertx vertx) {
    return VertxChannelBuilder.forAddress(vertx, "localhost", 8080)
        .usePlaintext()
        .intercept(new TestAuthClientInterceptor())
        .build();
  }
}
