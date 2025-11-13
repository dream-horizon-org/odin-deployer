package com.dream11.odin.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.dream11.grpc.error.GrpcException;
import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.error.OdinError;
import com.dream11.odin.error.OdinRestError;
import com.dream11.odin.setup.Setup;
import com.dream11.odin.util.TestUtil;
import com.dream11.queue.impl.sqs.SqsConfig;
import com.dream11.queue.impl.sqs.SqsConsumer;
import com.dream11.queue.impl.sqs.SqsProducer;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Slf4j
@ExtendWith(VertxExtension.class)
@WireMockTest(httpPort = 9443)
@ExtendWith({Setup.class})
class OperateIT {

  static SqsAsyncClient sqsClient;

  static SqsConsumer sqsRequestConsumer;

  static SqsProducer<String> sqsResponseProducer;

  static Connection connection;
  private static String authToken;

  @BeforeAll
  @SneakyThrows
  static void setup() {
    sqsClient =
        SqsAsyncClient.builder()
            .endpointOverride(
                URI.create(
                    System.getProperty(com.dream11.odin.Constants.SQS_REQUEST_QUEUE_ENDPOINT)))
            .region(
                Region.of(System.getProperty(com.dream11.odin.Constants.SQS_REQUEST_QUEUE_REGION)))
            .build();
    SqsConfig sqsRequestConfig = TestUtil.getSqsRequestConfig();
    SqsConfig sqsResponseConfig = TestUtil.getSqsResponseConfig();
    sqsRequestConsumer = new SqsConsumer(sqsRequestConfig, sqsClient);
    sqsResponseProducer = new SqsProducer<>(sqsResponseConfig, sqsClient, __ -> __);
    connection = TestUtil.getDatabaseConnection();
    TestUtil.executeSqlFromDir(connection, com.dream11.odin.Constants.CORE_SEED_DATA_DIR);
    TestUtil.executeSqlFile(connection, "RestService.sql");
    authToken = TestUtil.generateAuthToken();
  }

  @AfterAll
  static void tearDown() throws SQLException {
    TestUtil.truncateDatabase(connection);
    connection.close();
  }

  private ValidatableResponse getResponse(
      String environmentName,
      String serviceName,
      String componentName,
      Map<String, String> headers) {
    Map<String, Object> body = new HashMap<>();
    body.put("operationName", "scale");
    body.put("config", Map.of("replicas", 3));
    return TestUtil.execute(
        body,
        headers,
        null,
        spec ->
            spec.port(9000)
                .post(
                    "/v1/env/{environmentName}/service/{serviceName}/component/{componentName}/operate",
                    environmentName,
                    serviceName,
                    componentName));
  }

  @Test
  void testValidOperate() {
    // Act
    ValidatableResponse response =
        getResponse(
            "odin-operate-component",
            "odin-operate-component",
            "cj-frontend-v1",
            Map.of(Constants.AUTHORIZATION_KEY, authToken));
    // Assert
    response.statusCode(202);
  }

  @Test
  void testInvalidAuthorization() {
    // Arrange
    // Act
    ValidatableResponse response =
        getResponse(
            "environmentName",
            "serviceName",
            "componentName",
            Map.of(Constants.AUTHORIZATION_KEY, ""));
    JsonObject responseBody = TestUtil.extractBody(response);
    OdinRestError odinRestError = OdinRestError.FORBIDDEN_EXCEPTION;
    JsonObject expectedResponse =
        TestUtil.getErrorResponse(
            odinRestError.getErrorMessage(),
            odinRestError.getErrorMessage(),
            odinRestError.getErrorCode());
    // Assert

    response.statusCode(403);
    assertThat(responseBody).isEqualTo(expectedResponse);
  }

  @Test
  void testScaleInvalidEnvironmentName() {
    // Act
    ValidatableResponse response =
        getResponse(
            "scale3",
            "serviceName",
            "componentName",
            Map.of(Constants.AUTHORIZATION_KEY, authToken));
    JsonObject body = TestUtil.extractBody(response);
    GrpcException grpcException =
        ExceptionUtil.getException(OdinError.ENV_DOES_NOT_EXIST, "scale3");
    JsonObject expectedResponse =
        TestUtil.getErrorResponse("Not Found", grpcException.getErrorMessage(), "404");
    // Assert
    response.statusCode(404);
    assertThat(body).isEqualTo(expectedResponse);
  }
}
