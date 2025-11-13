package com.dream11.odin.grpc.logs;

import com.dream11.odin.Constants;
import com.dream11.odin.setup.Setup;
import com.dream11.odin.setup.TestChannelProvider;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.grpc.ManagedChannel;
import io.reactivex.Flowable;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Slf4j
@ExtendWith({VertxExtension.class, Setup.class})
@WireMockTest(httpPort = 9443)
@Timeout(value = 2, timeUnit = TimeUnit.MINUTES)
public class LogsServiceIT {
  static ManagedChannel channel;

  @BeforeEach
  public void createChannel(Vertx vertx) {
    channel = TestChannelProvider.createChannel(vertx);
  }

  @BeforeEach
  void setup() {
    Setup.setupMockLogServer();
  }

  @Test
  void testGetLogs(VertxTestContext testContext) {
    // Arrange
    String testTraceId = Constants.TEST_TRACE_ID;
    GetLogsRequest request =
        GetLogsRequest.newBuilder()
            .setTraceId(testTraceId)
            .setFollow(true)
            .addAllSearchAfterParams(new ArrayList<>())
            .build();

    // Act
    RxLogsServiceGrpc.RxLogsServiceStub RxLogsServiceStub = RxLogsServiceGrpc.newRxStub(channel);
    Flowable<GetLogsResponse> responseFlowable = RxLogsServiceStub.getLogs(request);

    // Assert
    responseFlowable
        .doOnNext(response -> log.info("Received logs: {}", response))
        .subscribe(
            response -> {
              assert response.getLogsCount() > 0;
              testContext.completeNow();
            },
            testContext::failNow);
  }

  @Test
  void testGetLogsNoIndexFound(VertxTestContext testContext) {
    // Arrange
    String testTraceId = "randomTraceId";
    GetLogsRequest request =
        GetLogsRequest.newBuilder()
            .setTraceId(testTraceId)
            .setFollow(true)
            .addAllSearchAfterParams(new ArrayList<>())
            .build();

    // Act
    RxLogsServiceGrpc.RxLogsServiceStub RxLogsServiceStub = RxLogsServiceGrpc.newRxStub(channel);
    Flowable<GetLogsResponse> responseFlowable = RxLogsServiceStub.getLogs(request);

    // Assert
    responseFlowable.subscribe(
        response -> testContext.failNow("Expected no logs, but received: " + response),
        throwable -> {
          Assertions.assertTrue(
              throwable
                  .getMessage()
                  .contains("Logs not found for traceId: %s".formatted(testTraceId)),
              "Expected no index found error");
          testContext.completeNow();
        });
  }
}
