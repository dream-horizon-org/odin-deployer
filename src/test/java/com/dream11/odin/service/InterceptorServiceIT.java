package com.dream11.odin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dream11.odin.Constants;
import com.dream11.odin.grpc.service.DeployServiceRequest;
import com.dream11.odin.grpc.service.DeployServiceResponse;
import com.dream11.odin.grpc.service.RxServiceServiceGrpc;
import com.dream11.odin.responseautomata.impl.ServiceResponseAutomata;
import com.dream11.odin.setup.InterceptorSetup;
import com.dream11.odin.setup.TestChannelProvider;
import com.dream11.odin.util.ApplicationUtil;
import com.dream11.odin.util.TestUtil;
import io.grpc.ManagedChannel;
import io.reactivex.Flowable;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration tests for InterceptorService. Uses a separate context (InterceptorSetup) to enable
 * interceptor functionality without affecting other integration tests.
 */
@Slf4j
@ExtendWith({VertxExtension.class, InterceptorSetup.class})
@Timeout(value = 2, timeUnit = TimeUnit.MINUTES)
class InterceptorServiceIT {

  static ManagedChannel channel;
  static Connection connection;

  @BeforeAll
  @SneakyThrows
  static void setup(Vertx vertx) {
    connection = TestUtil.getDatabaseConnection();
    TestUtil.executeSqlFromDir(connection, Constants.CORE_SEED_DATA_DIR);
    TestUtil.executeSqlFile(connection, "ServiceService.sql");
  }

  @BeforeEach
  void createChannel(Vertx vertx) {
    channel = TestChannelProvider.createChannel(vertx);
  }

  @AfterAll
  static void tearDown() throws SQLException {
    TestUtil.truncateDatabase(connection);
    connection.close();
  }

  /**
   * Test that interceptor is invoked during service deployment and modifies the payload. Verifies
   * that the interceptor adds markers and modifies configuration values.
   */
  @Test
  void shouldModifyPayloadWhenInterceptorIsInvokedDuringDeploy(VertxTestContext testContext) {
    // Arrange
    String environmentName = Constants.TEST_ENV_NAME;
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());

    DeployServiceRequest request = TestUtil.getDeployServiceRequest(serviceName, environmentName);
    RxServiceServiceGrpc.RxServiceServiceStub serviceStub = RxServiceServiceGrpc.newRxStub(channel);

    // Act
    Flowable<DeployServiceResponse> flowableResponse = serviceStub.deployService(request);
    ServiceResponseAutomata responseAutomata = new ServiceResponseAutomata();

    // Assert
    flowableResponse
        .doOnNext(
            deployServiceResponse -> {
              log.debug("Received message from deployer: {}", deployServiceResponse);
              responseAutomata.switchState(deployServiceResponse.getServiceResponse());
            })
        .subscribe(
            deployServiceResponse -> {
              if (responseAutomata.isComplete()) {
                log.info(
                    "Deployment completed successfully with interceptor for service: {}",
                    serviceName);

                // Verify interceptor modifications in the database
                try {
                  verifyInterceptorModifications(serviceName, environmentName);
                  testContext.completeNow();
                } catch (Exception e) {
                  testContext.failNow(e);
                }
              }
            },
            testContext::failNow);
  }

  /**
   * Test that interceptor modifies component provisioning configuration. Verifies that provisioning
   * params are modified by the interceptor.
   */
  @Test
  void shouldModifyProvisioningConfigWhenInterceptorIsInvoked(VertxTestContext testContext) {
    // Arrange
    String environmentName = Constants.TEST_ENV_NAME;
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());

    DeployServiceRequest request = TestUtil.getDeployServiceRequest(serviceName, environmentName);
    RxServiceServiceGrpc.RxServiceServiceStub serviceStub = RxServiceServiceGrpc.newRxStub(channel);

    // Act
    Flowable<DeployServiceResponse> flowableResponse = serviceStub.deployService(request);
    ServiceResponseAutomata responseAutomata = new ServiceResponseAutomata();

    // Assert
    flowableResponse
        .doOnNext(
            deployServiceResponse -> {
              log.debug("Received response: {}", deployServiceResponse);
              responseAutomata.switchState(deployServiceResponse.getServiceResponse());
            })
        .subscribe(
            deployServiceResponse -> {
              if (responseAutomata.isComplete()) {
                log.info(
                    "Deployment completed successfully with interceptor for service: {}",
                    serviceName);

                // Verify provisioning modifications
                try {
                  verifyProvisioningModifications(serviceName, environmentName);
                  testContext.completeNow();
                } catch (Exception e) {
                  testContext.failNow(e);
                }
              }
            },
            testContext::failNow);
  }

  /**
   * Test basic interceptor integration - deployment should complete successfully. This is a sanity
   * test to ensure the interceptor doesn't break the deployment flow.
   */
  @Test
  void shouldCompleteDeploymentSuccessfullyWhenInterceptorIsEnabled(VertxTestContext testContext) {
    // Arrange
    String environmentName = Constants.TEST_ENV_NAME;
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());

    DeployServiceRequest request = TestUtil.getDeployServiceRequest(serviceName, environmentName);
    RxServiceServiceGrpc.RxServiceServiceStub serviceStub = RxServiceServiceGrpc.newRxStub(channel);

    // Act
    Flowable<DeployServiceResponse> flowableResponse = serviceStub.deployService(request);
    ServiceResponseAutomata responseAutomata = new ServiceResponseAutomata();

    // Assert - deployment should complete with interceptor
    flowableResponse
        .doOnNext(
            deployServiceResponse -> {
              log.debug("Received response with interceptor: {}", deployServiceResponse);
              responseAutomata.switchState(deployServiceResponse.getServiceResponse());
            })
        .subscribe(
            deployServiceResponse -> {
              if (responseAutomata.isComplete()) {
                log.info(
                    "Deployment completed successfully with interceptor for service: {}",
                    serviceName);
                testContext.completeNow();
              }
            },
            error -> {
              // Log error for debugging
              log.error("Stream error occurred: {}", error.getMessage(), error);
              testContext.failNow(error);
            });
  }

  /**
   * Helper method to verify that the interceptor modified the component definition config. Checks
   * the database for interceptor markers and modified values.
   */
  @SneakyThrows
  private void verifyInterceptorModifications(String serviceName, String environmentName) {
    String query =
        "SELECT ct.config FROM component_task ct "
            + "JOIN service_task st ON ct.service_task_id = st.id "
            + "JOIN environment e ON st.env_id = e.id "
            + "WHERE st.name = ? AND e.name = ?";

    try (var statement = connection.prepareStatement(query)) {
      statement.setString(1, serviceName);
      statement.setString(2, environmentName);

      ResultSet rs = statement.executeQuery();
      assertThat(rs.next()).isTrue();

      String configJson = rs.getString("config");
      assertThat(configJson).isNotNull();

      log.info("Component config from DB: {}", configJson);

      // Verify interceptor modifications in definition.config
      assertThat(configJson).contains("interceptor_processed");
      assertThat(configJson).contains("interceptor_timestamp");
      assertThat(configJson).contains("testConfigValue-intercepted");
    }
  }

  /**
   * Helper method to verify that the interceptor modified the provisioning params. Checks the
   * database for interceptor markers and modified provisioning values.
   */
  @SneakyThrows
  private void verifyProvisioningModifications(String serviceName, String environmentName) {
    String query =
        "SELECT ct.config FROM component_task ct "
            + "JOIN service_task st ON ct.service_task_id = st.id "
            + "JOIN environment e ON st.env_id = e.id "
            + "WHERE st.name = ? AND e.name = ?";

    try (var statement = connection.prepareStatement(query)) {
      statement.setString(1, serviceName);
      statement.setString(2, environmentName);

      ResultSet rs = statement.executeQuery();
      assertThat(rs.next()).isTrue();

      String configJson = rs.getString("config");
      assertThat(configJson).isNotNull();

      log.info("Component config from DB (for provisioning check): {}", configJson);

      // Verify interceptor modifications in provisioning.params
      assertThat(configJson).contains("interceptor_modified");
      assertThat(configJson).contains("testParamValue-modified");
    }
  }
}
