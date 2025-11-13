package com.dream11.odin.grpc.auth;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.dream11.odin.Constants;
import com.dream11.odin.setup.Setup;
import com.dream11.odin.setup.TestChannelProvider;
import com.dream11.odin.util.TestUtil;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.grpc.ManagedChannel;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith({VertxExtension.class, Setup.class})
@WireMockTest(httpPort = 9443)
class AuthServiceIT {

  static ManagedChannel channel;
  static Connection connection;

  private RxAuthServiceGrpc.RxAuthServiceStub client;

  @BeforeAll
  static void setup(Vertx vertx) {

    channel = TestChannelProvider.createChannel(vertx);
  }

  @BeforeEach
  public void setUp() throws SQLException {
    connection = TestUtil.getDatabaseConnection();
    TestUtil.executeSqlFromDir(connection, Constants.CORE_SEED_DATA_DIR);
    client = RxAuthServiceGrpc.newRxStub(channel);
  }

  @Test
  void testGetAuthProvider(VertxTestContext testContext) {
    client
        .getAuthProvider(GetAuthProviderRequest.newBuilder().setOrgId(0L).build())
        .doOnSuccess(
            response -> {
              assertThat(response.getType()).isEqualTo("ANONYMOUS");
              testContext.completeNow();
            })
        .doOnError(testContext::failNow)
        .subscribe();
  }

  @Test
  void testGetUserToken(VertxTestContext testContext) {
    client
        .getUserToken(GetUserTokenRequest.newBuilder().setOrgId(0L).build())
        .doOnSuccess(
            response -> {
              assertThat(response.getToken()).isNotNull();
              assertThat(response.getToken()).isInstanceOf(String.class);
              testContext.completeNow();
            })
        .doOnError(testContext::failNow)
        .subscribe();
  }
}
