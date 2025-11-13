package com.dream11.odin.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.dream11.odin.constant.Constants;
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
class StatusIT {

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

  private ValidatableResponse getResponse(String serviceTaskId, Map<String, String> headers) {
    Map<String, Object> body = new HashMap<>();
    return TestUtil.execute(
        body,
        headers,
        null,
        spec -> spec.port(9000).get("/v1/operate/status/{serviceTaskId}", serviceTaskId));
  }

  @Test
  void testInvalidAuthorization() {
    // Act
    ValidatableResponse response = getResponse("5", Map.of(Constants.AUTHORIZATION_KEY, ""));
    JsonObject body = TestUtil.extractBody(response);
    OdinRestError odinRestError = OdinRestError.FORBIDDEN_EXCEPTION;
    JsonObject expectedResponse =
        TestUtil.getErrorResponse(
            odinRestError.getErrorMessage(),
            odinRestError.getErrorMessage(),
            odinRestError.getErrorCode());
    // Assert
    response.statusCode(403);
    assertThat(body).isEqualTo(expectedResponse);
  }

  @Test
  void testInvalidServiceTaskId() {
    // Arrange
    // Act
    ValidatableResponse response =
        getResponse("334", Map.of(Constants.AUTHORIZATION_KEY, authToken));
    JsonObject body = TestUtil.extractBody(response);
    OdinRestError odinRestError = OdinRestError.OPERATION_ID_INVALID;
    JsonObject expectedResponse =
        TestUtil.getErrorResponse(
            odinRestError.getErrorMessage(),
            odinRestError.getErrorMessage(),
            odinRestError.getErrorCode());
    // Assert
    response.statusCode(404);
    assertThat(body).isEqualTo(expectedResponse);
  }

  @Test
  void testValidServiceTaskId() {
    // Arrange
    // Act
    ValidatableResponse response = getResponse("5", Map.of(Constants.AUTHORIZATION_KEY, authToken));
    JsonObject body = TestUtil.extractBody(response);
    JsonObject expectedResponse = JsonObject.of("status", "SUCCESSFUL");
    // Assert
    response.statusCode(200);
    assertThat(body).isEqualTo(expectedResponse);
  }
}
