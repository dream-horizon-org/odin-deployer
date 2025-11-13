package com.dream11.odin.grpc.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.dream11.odin.grpc.health.v1.HealthCheckRequest;
import com.dream11.odin.grpc.health.v1.HealthCheckResponse;
import com.dream11.odin.grpc.health.v1.RxHealthGrpc;
import com.dream11.odin.setup.Setup;
import com.dream11.odin.setup.TestChannelProvider;
import io.grpc.ManagedChannel;
import io.reactivex.Single;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Slf4j
@ExtendWith({VertxExtension.class, Setup.class})
class HealthServiceIT {
  static ManagedChannel channel;

  @BeforeAll
  static void setup(Vertx vertx) {
    channel = TestChannelProvider.createChannel(vertx);
  }

  @AfterAll
  static void tearDown() {
    if (channel != null) {
      channel.shutdown();
    }
  }

  @Test
  void testHealthcheck(VertxTestContext testContext) {
    // Arrange
    RxHealthGrpc.RxHealthStub rxHealthStub = RxHealthGrpc.newRxStub(channel);

    // Act
    Single<HealthCheckResponse> responseSingle =
        rxHealthStub.check(HealthCheckRequest.getDefaultInstance());

    // Assert
    responseSingle
        .doOnSuccess(
            healthcheckResponse -> assertThat(healthcheckResponse.getStatusValue()).isEqualTo(1))
        .subscribe(healthcheckResponse -> testContext.completeNow(), testContext::failNow);
  }
}
