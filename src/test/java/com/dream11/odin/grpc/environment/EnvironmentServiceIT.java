package com.dream11.odin.grpc.environment;

import static com.dream11.odin.constant.Constants.ACCOUNT_PARAM;
import static com.dream11.odin.constant.Constants.COMPONENT_NAME_PARAM;
import static com.dream11.odin.constant.Constants.DISPLAY_ALL_PARAM;
import static com.dream11.odin.constant.Constants.SERVICE_NAME_PARAM;
import static com.dream11.odin.error.OdinError.ENV_CANNOT_BE_DELETED;
import static com.dream11.odin.oam.MockOAMProviderAccountService.accountNameWCluster;
import static com.dream11.odin.oam.MockOAMProviderAccountService.accountNameWoCluster;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.Constants;
import com.dream11.odin.FailedComponent;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dto.response.ResponseMessageType;
import com.dream11.odin.dto.v1.Environment;
import com.dream11.odin.dto.v1.EnvironmentSummary;
import com.dream11.odin.error.OdinError;
import com.dream11.odin.responseautomata.ResponseState;
import com.dream11.odin.responseautomata.impl.EnvResponseAutomata;
import com.dream11.odin.responseautomata.state.CreateEnvironment;
import com.dream11.odin.responseautomata.state.DeleteEnvironment;
import com.dream11.odin.setup.Setup;
import com.dream11.odin.setup.TestChannelProvider;
import com.dream11.odin.util.ApplicationUtil;
import com.dream11.odin.util.EnvironmentUtil;
import com.dream11.odin.util.TestUtil;
import com.dream11.queue.impl.sqs.SqsConfig;
import com.dream11.queue.impl.sqs.SqsProducer;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Slf4j
@ExtendWith({VertxExtension.class, Setup.class})
@WireMockTest(httpPort = 9443)
@Timeout(value = 5, timeUnit = TimeUnit.MINUTES)
class EnvironmentServiceIT {
  static ManagedChannel channel;

  static SqsAsyncClient sqsClient;

  static SqsProducer<String> sqsResponseProducer;

  static Connection connection;

  @BeforeAll
  @SneakyThrows
  static void setup(Vertx vertx) {
    channel = TestChannelProvider.createChannel(vertx);
    sqsClient =
        SqsAsyncClient.builder()
            .endpointOverride(URI.create(System.getProperty(Constants.SQS_REQUEST_QUEUE_ENDPOINT)))
            .region(Region.of(System.getProperty(Constants.SQS_REQUEST_QUEUE_REGION)))
            .build();
    SqsConfig sqsResponseConfig = TestUtil.getSqsResponseConfig();

    sqsResponseProducer = new SqsProducer<>(sqsResponseConfig, sqsClient, __ -> __);
    connection = TestUtil.getDatabaseConnection();
    TestUtil.executeSqlFromDir(connection, Constants.CORE_SEED_DATA_DIR);
    TestUtil.executeSqlFile(connection, "EnvironmentService.sql");
  }

  @AfterAll
  static void tearDown() throws SQLException {
    TestUtil.truncateDatabase(connection);
    connection.close();
  }

  @Test
  void testCreateEnvSuccessfulForNoCluster(VertxTestContext testContext) {
    // Arrange
    String environmentName = "testenvnc";
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    CreateEnvironmentRequest request =
        CreateEnvironmentRequest.newBuilder()
            .addAccounts(accountNameWoCluster)
            .setEnvName(environmentName)
            .build();
    String query = "SELECT id from environment where name=?;";

    // Act
    Flowable<CreateEnvironmentResponse> flowableResponse =
        RxEnvironmentServiceStub.createEnvironment(request);

    // Assert
    flowableResponse
        .doOnNext(
            response -> {
              assertThat(response)
                  .isEqualTo(
                      CreateEnvironmentResponse.newBuilder()
                          .setMessage("Env created successfully")
                          .build());
              try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, environmentName);
                ResultSet resultSet = preparedStatement.executeQuery();
                assertTrue(resultSet.next());
              }
            })
        .subscribe(createEnvironmentResponse -> testContext.completeNow(), testContext::failNow);
  }

  @Test
  void testCreateEnvSuccessfulForClusteredAccount(VertxTestContext testContext) {
    // Arrange
    String environmentName = "testenv2c";
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    CreateEnvironmentRequest request =
        CreateEnvironmentRequest.newBuilder()
            .addAccounts(accountNameWCluster)
            .setEnvName(environmentName)
            .build();
    ResponseState createEnvironment = new CreateEnvironment(true);
    EnvResponseAutomata responseAutomata =
        new EnvResponseAutomata(createEnvironment, createEnvironment);

    // Act
    Flowable<CreateEnvironmentResponse> flowableResponse =
        RxEnvironmentServiceStub.createEnvironment(request);

    // Assert
    flowableResponse
        .doOnNext(
            createEnvironmentResponse -> {
              log.debug("Received response: {}", createEnvironmentResponse);
              responseAutomata.switchState(createEnvironmentResponse);
            })
        .subscribe(
            createEnvironmentResponse -> {
              if (responseAutomata.isComplete()) {
                testContext.completeNow();
              }
            },
            testContext::failNow);
  }

  @Test
  void testCreateEnvFailureForClusteredAccount(VertxTestContext testContext) {
    // Arrange
    String environmentName = "testenv3c";
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    CreateEnvironmentRequest request =
        CreateEnvironmentRequest.newBuilder()
            .addAccounts(accountNameWCluster)
            .setEnvName(environmentName)
            .build();

    FailedComponent.addFailedNamespace(environmentName, Action.CREATE_ENVIRONMENT);

    // Act
    Flowable<CreateEnvironmentResponse> flowableResponse =
        RxEnvironmentServiceStub.createEnvironment(request);

    // Assert
    flowableResponse.subscribe(
        createEnvironmentResponse -> {},
        err -> {
          try {
            assertThat(err.getClass()).isEqualTo(StatusRuntimeException.class);
            assertThat(((StatusRuntimeException) err).getStatus().getCode().toString())
                .hasToString(OdinError.ENV_CREATION_FAILED.getGrpcCode().toString());
            assertThat(((StatusRuntimeException) err).getStatus().getDescription())
                .isEqualTo(OdinError.ENV_CREATION_FAILED.getErrorMessage(), environmentName);
            testContext.completeNow();
          } catch (Exception e) {
            testContext.failNow(e);
          }
        });
  }

  @Test
  void testEnvironmentResponseProcessedSuccessfully(VertxTestContext testContext)
      throws SQLException, InterruptedException, ExecutionException {

    // Arrange
    long taskId = 100;
    JsonObject responseMessage = new JsonObject();
    responseMessage.put("id", taskId);
    responseMessage.put("type", ResponseMessageType.NAMESPACE);
    responseMessage.put("status", TaskStatus.SUCCESSFUL);
    String query = "SELECT id, status from environment_task where id=?;";
    try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
      preparedStatement.setLong(1, taskId);

      // Act
      sqsResponseProducer.send(responseMessage.toString()).get();

      Awaitility.await()
          .atMost(5001, TimeUnit.MILLISECONDS)
          .pollDelay(500, TimeUnit.MILLISECONDS)
          .until(
              () -> {
                ResultSet resultSet = preparedStatement.executeQuery();
                resultSet.next();
                return !resultSet.getString("status").equals(TaskStatus.IN_PROGRESS.name());
              });

      // Assert
      ResultSet resultSet = preparedStatement.executeQuery();
      assertTrue(resultSet.next());
      assertEquals(TaskStatus.SUCCESSFUL.name(), resultSet.getString("status"));

      testContext.completeNow();
    }
  }

  @Test
  void testEnvironmentFailureResponseProcessedSuccessfully(VertxTestContext testContext)
      throws SQLException, ExecutionException, InterruptedException {

    // Arrange
    long taskId = 101;
    String errorMessage = "Cluster stability issue";
    JsonObject responseMessage = new JsonObject();
    responseMessage.put("id", taskId);
    responseMessage.put("type", ResponseMessageType.NAMESPACE);
    responseMessage.put("status", TaskStatus.FAILED);
    responseMessage.put("error", errorMessage);
    String envTaskQuery = "SELECT id, status, env_id from environment_task where id=?;";
    String envQuery = "SELECT id, is_active from environment where id=?;";
    try (PreparedStatement envTaskPreparedStatement = connection.prepareStatement(envTaskQuery);
        PreparedStatement envPreparedStatement = connection.prepareStatement(envQuery)) {

      envTaskPreparedStatement.setLong(1, taskId);

      // Act
      sqsResponseProducer.send(responseMessage.toString()).get();

      Awaitility.await()
          .atMost(5001, TimeUnit.MILLISECONDS)
          .pollDelay(500, TimeUnit.MILLISECONDS)
          .until(
              () -> {
                ResultSet resultSet = envTaskPreparedStatement.executeQuery();
                resultSet.next();
                return !resultSet.getString("status").equals(TaskStatus.IN_PROGRESS.name());
              });

      // Assert
      ResultSet resultSet = envTaskPreparedStatement.executeQuery();
      assertTrue(resultSet.next());
      assertEquals(TaskStatus.FAILED.name(), resultSet.getString("status"));

      envPreparedStatement.setLong(1, resultSet.getLong("env_id"));
      resultSet = envPreparedStatement.executeQuery();
      assertTrue(resultSet.next());
    }
    testContext.completeNow();
  }

  @Test
  void testCreateEnvFailureForDuplicateName(VertxTestContext testContext) {
    // Arrange
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    CreateEnvironmentRequest request =
        CreateEnvironmentRequest.newBuilder()
            .addAccounts(accountNameWoCluster)
            .setEnvName("testenv-c")
            .build();

    Flowable<CreateEnvironmentResponse> flowableResponse =
        RxEnvironmentServiceStub.createEnvironment(request);
    flowableResponse
        .doOnNext(
            response -> {
              // Act
              Flowable<CreateEnvironmentResponse> flowableDuplicateResponse =
                  RxEnvironmentServiceStub.createEnvironment(request);

              // Assert
              flowableDuplicateResponse.subscribe(
                  r -> testContext.failNow("Should have thrown env already exists"),
                  err -> {
                    assertThat(err.getClass()).isEqualTo(StatusRuntimeException.class);
                    assertThat(((StatusRuntimeException) err).getStatus().getCode().toString())
                        .hasToString(OdinError.ENV_ALREADY_EXISTS.getGrpcCode().toString());
                    assertThat(((StatusRuntimeException) err).getStatus().getDescription())
                        .isEqualTo(
                            OdinError.ENV_ALREADY_EXISTS.getErrorMessage(),
                            "CREATE_ENVIRONMENT_IN_PROGRESS");
                  });
            })
        .subscribe(createEnvironmentResponse -> testContext.completeNow(), testContext::failNow);
  }

  @Test
  void testCreateEnvFailureForInvalidName(VertxTestContext testContext) {
    // Arrange
    String environmentName = "invalid_Env1";
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    CreateEnvironmentRequest request =
        CreateEnvironmentRequest.newBuilder()
            .addAccounts(accountNameWCluster)
            .setEnvName(environmentName)
            .build();

    // Act
    Flowable<CreateEnvironmentResponse> flowableResponse =
        RxEnvironmentServiceStub.createEnvironment(request);

    // Assert
    flowableResponse.subscribe(
        createEnvironmentResponse -> testContext.failNow("Should have thrown invalid env name"),
        err -> {
          try {
            assertThat(err.getClass()).isEqualTo(StatusRuntimeException.class);
            assertThat(((StatusRuntimeException) err).getStatus().getCode().toString())
                .hasToString(OdinError.INVALID_ENV_NAME.getGrpcCode().toString());
            assertThat(((StatusRuntimeException) err).getStatus().getDescription())
                .isEqualTo(
                    OdinError.INVALID_ENV_NAME.getErrorMessage(),
                    environmentName,
                    "^(?=.{1,63}$)[a-z0-9][a-z0-9-]*[a-z0-9]$");
            testContext.completeNow();
          } catch (Throwable e) {
            testContext.failNow(e);
          }
        });
  }

  @Test
  void testListEnvironmentWithoutDisplayAll(VertxTestContext testContext) {
    // Arrange
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    ListEnvironmentRequest request = ListEnvironmentRequest.newBuilder().build();

    // Act
    Single<ListEnvironmentResponse> responseSingle =
        RxEnvironmentServiceStub.listEnvironment(request);

    // Assert
    responseSingle
        .doOnSuccess(
            response -> {
              List<EnvironmentSummary> envs = response.getEnvironmentsList();
              Optional<EnvironmentSummary> environment196 =
                  envs.stream().filter(env -> env.getName().equals("env196")).findFirst();
              Optional<EnvironmentSummary> environment1 =
                  envs.stream()
                      .filter(env -> env.getName().equals("env-anonymous-user"))
                      .findFirst();
              assertThat(environment196).isNotPresent();
              assertThat(environment1).isPresent();
            })
        .subscribe(listEnvironmentResponse -> testContext.completeNow(), testContext::failNow);
  }

  @Test
  void testListEnvironmentWithAccount(VertxTestContext testContext) {
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    ListEnvironmentRequest request =
        ListEnvironmentRequest.newBuilder().putParams(ACCOUNT_PARAM, "staging").build();

    Single<ListEnvironmentResponse> responseSingle =
        RxEnvironmentServiceStub.listEnvironment(request);

    responseSingle
        .doOnSuccess(
            response -> {
              List<EnvironmentSummary> envs = response.getEnvironmentsList();
              Optional<EnvironmentSummary> environment1 =
                  envs.stream().filter(env -> env.getName().equals("env1")).findFirst();
              Optional<EnvironmentSummary> environment196 =
                  envs.stream().filter(env -> env.getName().equals("env196")).findFirst();
              assertThat(environment1).isPresent();
              assertThat(environment196).isPresent();
            })
        .subscribe(listEnvironmentResponse -> testContext.completeNow(), testContext::failNow);
  }

  @Test
  void testListEnvironmentWithDisplayAll(VertxTestContext testContext) {
    // Arrange
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    ListEnvironmentRequest request =
        ListEnvironmentRequest.newBuilder().putParams(DISPLAY_ALL_PARAM, "true").build();

    // Act
    Single<ListEnvironmentResponse> responseSingle =
        RxEnvironmentServiceStub.listEnvironment(request);

    // Assert
    responseSingle
        .doOnSuccess(
            response -> {
              List<EnvironmentSummary> envs = response.getEnvironmentsList();
              Optional<EnvironmentSummary> environment1 =
                  envs.stream().filter(env -> env.getName().equals("env1")).findFirst();
              Optional<EnvironmentSummary> environment196 =
                  envs.stream().filter(env -> env.getName().equals("env196")).findFirst();
              assertThat(environment1).isPresent();
              assertThat(environment196).isPresent();
            })
        .subscribe(listEnvironmentResponse -> testContext.completeNow(), testContext::failNow);
  }

  @Test
  void testDescribeEnvironmentFailIfEnvNameMissing(VertxTestContext testContext) {
    // Arrange
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    DescribeEnvironmentRequest request = DescribeEnvironmentRequest.newBuilder().build();

    // Act
    Single<DescribeEnvironmentResponse> responseSingle =
        RxEnvironmentServiceStub.describeEnvironment(request);

    // Assert
    responseSingle.subscribe(
        describeEnvironmentResponse -> testContext.failNow("Should throw env name missing"),
        err -> {
          try {
            assertThat(err.getClass()).isEqualTo(StatusRuntimeException.class);
            assertThat(((StatusRuntimeException) err).getStatus().getCode().toString())
                .hasToString(OdinError.ENV_NAME_MISSING.getGrpcCode().toString());
            assertThat(((StatusRuntimeException) err).getStatus().getDescription())
                .isEqualTo(OdinError.ENV_NAME_MISSING.getErrorMessage());
            testContext.completeNow();
          } catch (Throwable e) {
            testContext.failNow(e);
          }
        });
  }

  @Test
  void testDescribeEnvironmentFailIfEnvNotFound(VertxTestContext testContext) {
    // Arrange
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    DescribeEnvironmentRequest request =
        DescribeEnvironmentRequest.newBuilder().setEnvName("wrong_env").build();

    // Act
    Single<DescribeEnvironmentResponse> responseSingle =
        RxEnvironmentServiceStub.describeEnvironment(request);

    // Assert
    responseSingle.subscribe(
        describeEnvironmentResponse -> testContext.failNow("Should throw env not found"),
        err -> {
          try {
            assertThat(err.getClass()).isEqualTo(StatusRuntimeException.class);
            assertThat(((StatusRuntimeException) err).getStatus().getCode().toString())
                .hasToString(OdinError.ENV_DOES_NOT_EXIST.getGrpcCode().toString());
            assertThat(((StatusRuntimeException) err).getStatus().getDescription())
                .isEqualTo(OdinError.ENV_DOES_NOT_EXIST.getErrorMessage(), "wrong_env");
            testContext.completeNow();
          } catch (Throwable e) {
            testContext.failNow(e);
          }
        });
  }

  @Test
  void testDescribeEnvironmentFailIfEnvNotFoundWithService(VertxTestContext testContext) {
    // Arrange
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    DescribeEnvironmentRequest request =
        DescribeEnvironmentRequest.newBuilder()
            .setEnvName("wrong_env")
            .putParams(SERVICE_NAME_PARAM, "some_service")
            .build();

    // Act
    Single<DescribeEnvironmentResponse> responseSingle =
        RxEnvironmentServiceStub.describeEnvironment(request);

    // Assert
    responseSingle.subscribe(
        describeEnvironmentResponse -> testContext.failNow("Should throw env not found"),
        err -> {
          try {
            assertThat(err.getClass()).isEqualTo(StatusRuntimeException.class);
            assertThat(((StatusRuntimeException) err).getStatus().getCode().toString())
                .hasToString(OdinError.ENV_DOES_NOT_EXIST.getGrpcCode().toString());
            assertThat(((StatusRuntimeException) err).getStatus().getDescription())
                .isEqualTo(OdinError.ENV_DOES_NOT_EXIST.getErrorMessage(), "wrong_env");
            testContext.completeNow();
          } catch (Throwable e) {
            testContext.failNow(e);
          }
        });
  }

  @Test
  void testDescribeEnvironmentFailIfServiceNotFound(VertxTestContext testContext) {
    // Arrange
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    DescribeEnvironmentRequest request =
        DescribeEnvironmentRequest.newBuilder()
            .setEnvName("env196")
            .putParams(SERVICE_NAME_PARAM, "wrong_service")
            .build();

    // Act
    Single<DescribeEnvironmentResponse> responseSingle =
        RxEnvironmentServiceStub.describeEnvironment(request);

    // Assert
    responseSingle.subscribe(
        describeEnvironmentResponse -> testContext.failNow("Should throw service not found in env"),
        err -> {
          try {
            assertThat(err.getClass()).isEqualTo(StatusRuntimeException.class);
            testContext.completeNow();
          } catch (Throwable e) {
            testContext.failNow(e);
          }
        });
  }

  @Test
  void testDescribeEnvironmentFailIfEnvNotFoundWithServiceAndComponent(
      VertxTestContext testContext) {
    // Arrange
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    DescribeEnvironmentRequest request =
        DescribeEnvironmentRequest.newBuilder()
            .setEnvName("wrong_env")
            .putParams(SERVICE_NAME_PARAM, "some_service")
            .putParams(COMPONENT_NAME_PARAM, "some_component")
            .build();

    // Act
    Single<DescribeEnvironmentResponse> responseSingle =
        RxEnvironmentServiceStub.describeEnvironment(request);

    // Assert
    responseSingle.subscribe(
        describeEnvironmentResponse -> testContext.failNow("Should throw env not found"),
        err -> {
          try {
            assertThat(err.getClass()).isEqualTo(StatusRuntimeException.class);
            assertThat(((StatusRuntimeException) err).getStatus().getCode().toString())
                .hasToString(OdinError.ENV_DOES_NOT_EXIST.getGrpcCode().toString());
            assertThat(((StatusRuntimeException) err).getStatus().getDescription())
                .isEqualTo(OdinError.ENV_DOES_NOT_EXIST.getErrorMessage(), "wrong_env");
            testContext.completeNow();
          } catch (Throwable e) {
            testContext.failNow(e);
          }
        });
  }

  @Test
  void testDescribeEnvironmentFailIfServiceNotFoundWithComponent(VertxTestContext testContext) {
    // Arrange
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    DescribeEnvironmentRequest request =
        DescribeEnvironmentRequest.newBuilder()
            .setEnvName("env196")
            .putParams(SERVICE_NAME_PARAM, "wrong_service")
            .putParams(COMPONENT_NAME_PARAM, "some_component")
            .build();

    // Act
    Single<DescribeEnvironmentResponse> responseSingle =
        RxEnvironmentServiceStub.describeEnvironment(request);

    // Assert
    responseSingle.subscribe(
        describeEnvironmentResponse -> testContext.failNow("Should throw service not found in env"),
        err -> {
          try {
            assertThat(err.getClass()).isEqualTo(StatusRuntimeException.class);
            assertThat(((StatusRuntimeException) err).getStatus().getCode().toString())
                .hasToString(
                    OdinError.SERVICE_OR_COMPONENT_DOES_NOT_EXIST_IN_ENV.getGrpcCode().toString());
            assertThat(((StatusRuntimeException) err).getStatus().getDescription())
                .isEqualTo(
                    OdinError.SERVICE_OR_COMPONENT_DOES_NOT_EXIST_IN_ENV.getErrorMessage(),
                    "wrong_service",
                    "some_component",
                    "env196");
            testContext.completeNow();
          } catch (Throwable e) {
            testContext.failNow(e);
          }
        });
  }

  @Test
  void testDescribeEnvironmentFailIfComponentNotFound(VertxTestContext testContext) {
    // Arrange
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    DescribeEnvironmentRequest request =
        DescribeEnvironmentRequest.newBuilder()
            .setEnvName("env196")
            .putParams(SERVICE_NAME_PARAM, "service1")
            .putParams(COMPONENT_NAME_PARAM, "wrong_component")
            .build();

    // Act
    Single<DescribeEnvironmentResponse> responseSingle =
        RxEnvironmentServiceStub.describeEnvironment(request);

    // Assert
    responseSingle.subscribe(
        describeEnvironmentResponse ->
            testContext.failNow("Should throw component not found in service"),
        err -> {
          try {
            assertThat(err.getClass()).isEqualTo(StatusRuntimeException.class);
            assertThat(((StatusRuntimeException) err).getStatus().getCode().toString())
                .hasToString(
                    OdinError.SERVICE_OR_COMPONENT_DOES_NOT_EXIST_IN_ENV.getGrpcCode().toString());
            assertThat(((StatusRuntimeException) err).getStatus().getDescription())
                .isEqualTo(
                    OdinError.SERVICE_OR_COMPONENT_DOES_NOT_EXIST_IN_ENV.getErrorMessage(),
                    "service1",
                    "wrong_component",
                    "env196");
            testContext.completeNow();
          } catch (Throwable e) {
            testContext.failNow(e);
          }
        });
  }

  @Test
  void testDescribeEnvironmentFailIfJustComponentNameProvided(VertxTestContext testContext) {
    // Arrange
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    DescribeEnvironmentRequest request =
        DescribeEnvironmentRequest.newBuilder()
            .setEnvName("env196")
            .putParams(COMPONENT_NAME_PARAM, "component_name_1")
            .build();

    // Act
    Single<DescribeEnvironmentResponse> responseSingle =
        RxEnvironmentServiceStub.describeEnvironment(request);

    // Assert
    responseSingle.subscribe(
        describeEnvironmentResponse ->
            testContext.failNow("Should throw provide both service name and component name"),
        err -> {
          try {
            assertThat(err.getClass()).isEqualTo(StatusRuntimeException.class);
            assertThat(((StatusRuntimeException) err).getStatus().getCode().toString())
                .hasToString(OdinError.PROVIDE_BOTH_SERVICE_AND_COMPONENT.getGrpcCode().toString());
            assertThat(((StatusRuntimeException) err).getStatus().getDescription())
                .isEqualTo(OdinError.PROVIDE_BOTH_SERVICE_AND_COMPONENT.getErrorMessage());
            testContext.completeNow();
          } catch (Throwable e) {
            testContext.failNow(e);
          }
        });
  }

  @Test
  void testDescribeEnvironmentWithJustEnvName(VertxTestContext testContext) {
    // Arrange
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    DescribeEnvironmentRequest request =
        DescribeEnvironmentRequest.newBuilder().setEnvName("env196").build();

    // Act
    Single<DescribeEnvironmentResponse> responseSingle =
        RxEnvironmentServiceStub.describeEnvironment(request);

    // Assert
    responseSingle
        .doOnSuccess(
            response -> {
              Environment env = response.getEnvironment();
              assertThat(env.getName()).isEqualTo("env196");
              assertThat(env.getServicesCount()).isEqualTo(1);
            })
        .subscribe(describeEnvironmentResponse -> testContext.completeNow(), testContext::failNow);
  }

  @Test
  void testDescribeEnvironmentWithJustEnvNameHavingNoService(VertxTestContext testContext) {
    // Arrange
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    DescribeEnvironmentRequest request =
        DescribeEnvironmentRequest.newBuilder().setEnvName("env1").build();

    // Act
    Single<DescribeEnvironmentResponse> responseSingle =
        RxEnvironmentServiceStub.describeEnvironment(request);

    // Assert
    responseSingle
        .doOnSuccess(
            response -> {
              Environment env = response.getEnvironment();
              assertThat(env.getName()).isEqualTo("env1");
              assertThat(env.getServicesCount()).isEqualTo(1);
            })
        .subscribe(describeEnvironmentResponse -> testContext.completeNow(), testContext::failNow);
  }

  @Test
  void testDescribeEnvironmentWithServiceName(VertxTestContext testContext) {
    // Arrange
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    DescribeEnvironmentRequest request =
        DescribeEnvironmentRequest.newBuilder()
            .setEnvName("env196")
            .putParams(SERVICE_NAME_PARAM, "service1")
            .build();

    // Act
    Single<DescribeEnvironmentResponse> responseSingle =
        RxEnvironmentServiceStub.describeEnvironment(request);

    // Assert
    responseSingle
        .doOnSuccess(
            response -> {
              Environment env = response.getEnvironment();
              assertThat(env.getServicesCount()).isEqualTo(1);
              assertThat(env.getServices(0).getName()).isEqualTo("service1");
              assertThat(env.getServices(0).getComponentsCount()).isEqualTo(2);
              assertConfigMerged(
                  env.getServices(0).getComponents(0).getName(),
                  env.getServices(0).getComponents(0).getConfigJson());
              assertConfigMerged(
                  env.getServices(0).getComponents(1).getName(),
                  env.getServices(0).getComponents(1).getConfigJson());
            })
        .subscribe(describeEnvironmentResponse -> testContext.completeNow(), testContext::failNow);
  }

  @Test
  void testDescribeEnvironmentWithComponentName(VertxTestContext testContext) {
    // Arrange
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    DescribeEnvironmentRequest request =
        DescribeEnvironmentRequest.newBuilder()
            .setEnvName("env196")
            .putParams(SERVICE_NAME_PARAM, "service1")
            .putParams(COMPONENT_NAME_PARAM, "component1s1")
            .build();

    // Act
    Single<DescribeEnvironmentResponse> responseSingle =
        RxEnvironmentServiceStub.describeEnvironment(request);

    // Assert
    responseSingle
        .doOnSuccess(
            response -> {
              Environment env = response.getEnvironment();
              assertThat(env.getServices(0).getComponentsCount()).isEqualTo(1);
              assertThat(env.getServices(0).getComponents(0).getName()).isEqualTo("component1s1");
              assertThat(env.getServices(0).getComponents(0).getType()).isEqualTo("application");
              assertUndeployedComponentConfigNotPresent(
                  "component1s1", env.getServices(0).getComponents(0).getConfigJson());
              assertConfigMerged(
                  env.getServices(0).getComponents(0).getName(),
                  env.getServices(0).getComponents(0).getConfigJson());
            })
        .subscribe(describeEnvironmentResponse -> testContext.completeNow(), testContext::failNow);
  }

  private void assertUndeployedComponentConfigNotPresent(String componentName, String configJson)
      throws SQLException {
    ResultSet componentTask =
        TestUtil.fetchLatestComponentTask(connection, "DEPLOY", componentName);
    JsonObject mergedConfigJson = new JsonObject(configJson);
    List<Pair<Long, String>> statusConfigPairUntilUndeployed = new ArrayList<>();
    List<Pair<Long, String>> statusConfigPairAfterUndeployed = new ArrayList<>();
    boolean foundUndeployed = false;
    while (componentTask.next()) {
      foundUndeployed = foundUndeployed || componentTask.getLong("action_id") == 2;
      if (!foundUndeployed) {
        statusConfigPairAfterUndeployed.add(
            Pair.of(componentTask.getLong("action_id"), componentTask.getString("config")));
      } else {
        statusConfigPairUntilUndeployed.add(
            Pair.of(componentTask.getLong("action_id"), componentTask.getString("config")));
      }
    }
    Set<String> componentConfigKeyUntilUndeployed = new HashSet<>();
    Set<String> provisioningConfigKeyUntilUndeployed = new HashSet<>();

    for (Pair<Long, String> statusConfigPair : statusConfigPairUntilUndeployed) {
      JsonObject dbConfig = new JsonObject(statusConfigPair.getRight());
      JsonObject compJson = dbConfig.getJsonObject("componentConfig").getJsonObject("config");
      componentConfigKeyUntilUndeployed.add(compJson.getString("testCKey"));
      JsonObject provJson = dbConfig.getJsonObject("provisioningConfig").getJsonObject("params");
      provisioningConfigKeyUntilUndeployed.add(provJson.getString("testPKey"));
    }
    Set<String> componentConfigKeyAfterUndeployed = new HashSet<>();
    Set<String> provisioningConfigKeyAfterUndeployed = new HashSet<>();
    for (Pair<Long, String> statusConfigPair : statusConfigPairAfterUndeployed) {
      JsonObject dbConfig = new JsonObject(statusConfigPair.getRight());
      JsonObject compJson = dbConfig.getJsonObject("componentConfig").getJsonObject("config");
      componentConfigKeyAfterUndeployed.add(compJson.getString("testCKey"));
      JsonObject provJson = dbConfig.getJsonObject("provisioningConfig").getJsonObject("params");
      provisioningConfigKeyAfterUndeployed.add(provJson.getString("testPKey"));
    }
    String mergedConfigCompJsonValue = mergedConfigJson.getString("testCKey");
    String mergedConfigProvJsonValue = mergedConfigJson.getString("testPKey");

    assertThat(componentConfigKeyUntilUndeployed).doesNotContain(mergedConfigCompJsonValue);
    assertThat(provisioningConfigKeyUntilUndeployed).doesNotContain(mergedConfigProvJsonValue);

    assertThat(componentConfigKeyAfterUndeployed).contains(mergedConfigCompJsonValue);
    assertThat(provisioningConfigKeyAfterUndeployed).contains(mergedConfigProvJsonValue);
  }

  private void assertConfigMerged(String componentName, String configJson) throws SQLException {
    ResultSet componentTask =
        TestUtil.fetchLatestComponentTask(connection, "DEPLOY", componentName);
    assertThat(componentTask.next()).isTrue();
    JsonObject dbConfig = new JsonObject(componentTask.getString("config"));
    JsonObject mergedConfigJson = new JsonObject(configJson);
    JsonObject compJson = dbConfig.getJsonObject("componentConfig").getJsonObject("config");
    JsonObject provJson = dbConfig.getJsonObject("provisioningConfig").getJsonObject("params");
    JsonObject operateJson = dbConfig.getJsonObject("operationConfig");

    JsonObject mergedDbJson = new JsonObject();

    if (operateJson != null) {
      operateJson.forEach(entry -> mergedDbJson.put(entry.getKey(), entry.getValue()));
    } else {
      compJson.forEach(entry -> mergedDbJson.put(entry.getKey(), entry.getValue()));
      provJson.forEach(entry -> mergedDbJson.put(entry.getKey(), entry.getValue()));
    }

    // check if mergedConfigJson has same keys as mergedDBJson
    assertIsSubJson(mergedDbJson, mergedConfigJson);

    // check if operateJson overrides mergedConfigJson
    assertOperateConfigOverrides(operateJson, mergedConfigJson);
  }

  private void assertOperateConfigOverrides(JsonObject operateJson, JsonObject mergedConfigJson) {
    if (operateJson == null) {
      return;
    }
    for (Map.Entry<String, Object> entry : operateJson) {
      if (!mergedConfigJson.containsKey(entry.getKey())) {
        continue;
      }
      if (entry.getValue() instanceof JsonObject value) {
        assertOperateConfigOverrides(value, mergedConfigJson.getJsonObject(entry.getKey()));
      } else {
        assertThat(mergedConfigJson.getValue(entry.getKey())).isEqualTo(entry.getValue());
      }
    }
  }

  private static void assertIsSubJson(JsonObject mergedDbJson, JsonObject mergedConfigJson) {
    for (Map.Entry<String, Object> entry : mergedDbJson) {
      assertThat(mergedConfigJson.containsKey(entry.getKey())).isTrue();
      if (entry.getValue() instanceof JsonObject) {
        assertIsSubJson(
            (JsonObject) entry.getValue(), mergedConfigJson.getJsonObject(entry.getKey()));
      } else {
        if (mergedConfigJson.getValue(entry.getKey()) instanceof Double
            && entry.getValue() instanceof Integer) {
          assertThat(mergedConfigJson.getDouble(entry.getKey()))
              .isEqualTo(((Integer) entry.getValue()).doubleValue());
        } else {
          assertThat(mergedConfigJson.getValue(entry.getKey())).isEqualTo(entry.getValue());
        }
      }
    }
  }

  @Test
  void testDeleteEnvironment(VertxTestContext testContext) {
    // Arrange
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);

    String envName = "env-for-delete";
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    String componentName =
        String.format(
            "%s-%d", Constants.TEST_COMPONENT_NAME, ApplicationUtil.generateIntegerUUID());
    JsonObject componentConfig =
        TestUtil.getComponentConfig(
            componentName,
            Constants.TEST_CONFIG_KEY,
            Constants.TEST_PARAM_KEY,
            "container",
            Map.of());

    // Create service task
    int taskId =
        TestUtil.createServiceTask(
            connection, serviceName, envName, TaskStatus.SUCCESSFUL, Action.DEPLOY, 1);
    // Create component task
    TestUtil.createComponentTask(
        connection, taskId, componentName, componentConfig, TaskStatus.SUCCESSFUL, Action.DEPLOY);

    DeleteEnvironmentRequest deleteEnvironmentRequest =
        DeleteEnvironmentRequest.newBuilder().setEnvName(envName).build();

    ResponseState finalState = new DeleteEnvironment(true);
    EnvResponseAutomata envResponseAutomata = new EnvResponseAutomata(finalState, finalState);

    // Act
    Flowable<DeleteEnvironmentResponse> flowableResponse =
        RxEnvironmentServiceStub.deleteEnvironment(deleteEnvironmentRequest);

    // Assert
    flowableResponse
        .doOnNext(
            deleteEnvironmentResponse -> {
              log.debug(
                  String.format("Received message from deployer: %s", deleteEnvironmentResponse));
              envResponseAutomata.switchState(deleteEnvironmentResponse);
            })
        .subscribe(
            deleteEnvironmentResponse -> {
              if (envResponseAutomata.isComplete()) {
                TestUtil.assertServiceTaskStatus(
                    connection, serviceName, Action.UNDEPLOY, TaskStatus.SUCCESSFUL);
                TestUtil.assertComponentTaskStatus(
                    connection, componentName, Action.UNDEPLOY, TaskStatus.SUCCESSFUL);
                FailedComponent.reset();
                testContext.completeNow();
              }
            },
            testContext::failNow);
  }

  @Test
  void testDeleteEnvironmentServicesPartialFailed(VertxTestContext testContext) {
    // Arrange
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    String envName = "env-delete-partial-failure";
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    String componentName =
        String.format(
            "%s-%d", Constants.TEST_COMPONENT_NAME, ApplicationUtil.generateIntegerUUID());
    JsonObject componentConfig =
        TestUtil.getComponentConfig(
            componentName,
            Constants.TEST_CONFIG_KEY,
            Constants.TEST_PARAM_KEY,
            "container",
            Map.of());

    String serviceName2 =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    String componentName2 =
        String.format(
            "%s-%d", Constants.TEST_COMPONENT_NAME, ApplicationUtil.generateIntegerUUID());
    JsonObject componentConfig2 =
        TestUtil.getComponentConfig(
            componentName2,
            Constants.TEST_CONFIG_KEY,
            Constants.TEST_PARAM_KEY,
            "container",
            Map.of());

    FailedComponent.addFailedServiceComponent(
        Pair.of(serviceName2, Action.UNDEPLOY), List.of(Pair.of(componentName2, Action.UNDEPLOY)));

    // Create service task
    int taskId =
        TestUtil.createServiceTask(
            connection, serviceName, envName, TaskStatus.SUCCESSFUL, Action.DEPLOY, 1);
    int taskId2 =
        TestUtil.createServiceTask(
            connection, serviceName2, envName, TaskStatus.SUCCESSFUL, Action.DEPLOY, 2);
    // Create component task
    TestUtil.createComponentTask(
        connection, taskId, componentName, componentConfig, TaskStatus.SUCCESSFUL, Action.DEPLOY);
    TestUtil.createComponentTask(
        connection,
        taskId2,
        componentName2,
        componentConfig2,
        TaskStatus.SUCCESSFUL,
        Action.DEPLOY);

    DeleteEnvironmentRequest deleteEnvironmentRequest =
        DeleteEnvironmentRequest.newBuilder().setEnvName(envName).build();

    Flowable<DeleteEnvironmentResponse> flowableResponse =
        RxEnvironmentServiceStub.deleteEnvironment(deleteEnvironmentRequest);

    flowableResponse.subscribe(
        deleteEnvironmentResponse -> {
          log.debug("Received response from deployer: {}", deleteEnvironmentResponse);

          if (deleteEnvironmentResponse
              .getMessage()
              .contains(EnvironmentUtil.getStatus(Action.DELETE_ENVIRONMENT, TaskStatus.FAILED))) {
            TestUtil.assertServiceTaskStatus(
                connection, serviceName, Action.UNDEPLOY, TaskStatus.SUCCESSFUL);
            TestUtil.assertComponentTaskStatus(
                connection, componentName, Action.UNDEPLOY, TaskStatus.SUCCESSFUL);
            TestUtil.assertServiceTaskStatus(
                connection, serviceName2, Action.UNDEPLOY, TaskStatus.FAILED);
            TestUtil.assertComponentTaskStatus(
                connection, componentName2, Action.UNDEPLOY, TaskStatus.FAILED);
            testContext.completeNow();
          }
        },
        testContext::failNow);
  }

  @Test
  void testDeleteEnvironmentFailIfEnvNameMissing(VertxTestContext testContext) {
    // Arrange
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    DeleteEnvironmentRequest request = DeleteEnvironmentRequest.newBuilder().build();

    // Act
    Flowable<DeleteEnvironmentResponse> responseSingle =
        RxEnvironmentServiceStub.deleteEnvironment(request);

    // Assert
    responseSingle.subscribe(
        deleteEnvironmentResponse -> testContext.failNow("Should throw env name missing"),
        err -> {
          try {
            assertThat(err.getClass()).isEqualTo(StatusRuntimeException.class);
            assertThat(err.getMessage())
                .contains(
                    ExceptionUtil.getException(OdinError.ENV_DOES_NOT_EXIST, "").getErrorMessage());
            testContext.completeNow();
          } catch (Throwable e) {
            testContext.failNow(e);
          }
        });
  }

  @Test
  void testDeleteEnvironmentFailIfEnvStateNotDeletable(VertxTestContext testContext) {
    // Arrange
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    DeleteEnvironmentRequest request =
        DeleteEnvironmentRequest.newBuilder().setEnvName("env-delete-failure").build();

    // Act
    Flowable<DeleteEnvironmentResponse> responseSingle =
        RxEnvironmentServiceStub.deleteEnvironment(request);

    // Assert
    responseSingle.subscribe(
        deleteEnvironmentResponse ->
            testContext.failNow("Should throw error for unacceptable state for deletion"),
        err -> {
          try {
            assertThat(err.getClass()).isEqualTo(StatusRuntimeException.class);
            assertThat(err.getMessage())
                .contains(
                    ExceptionUtil.getException(
                            ENV_CANNOT_BE_DELETED,
                            EnvironmentUtil.getStatus(
                                Action.DELETE_ENVIRONMENT, TaskStatus.SUCCESSFUL))
                        .getErrorMessage());
            testContext.completeNow();
          } catch (Throwable e) {
            testContext.failNow(e);
          }
        });
  }

  @Test
  void testDeleteEnvironmentFailIfAllServicesNotUndeployed(VertxTestContext testContext) {
    // Arrange
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    DeleteEnvironmentRequest request =
        DeleteEnvironmentRequest.newBuilder().setEnvName("env-delete-failure").build();

    // Act
    Flowable<DeleteEnvironmentResponse> responseSingle =
        RxEnvironmentServiceStub.deleteEnvironment(request);

    // Assert
    responseSingle.subscribe(
        record -> testContext.failNow("Should throw error for unacceptable state for deletion"),
        err -> {
          try {
            assertThat(err.getClass()).isEqualTo(StatusRuntimeException.class);
            assertThat(err.getMessage())
                .contains(
                    ExceptionUtil.getException(
                            ENV_CANNOT_BE_DELETED,
                            EnvironmentUtil.getStatus(
                                Action.DELETE_ENVIRONMENT, TaskStatus.SUCCESSFUL))
                        .getErrorMessage());
            testContext.completeNow();
          } catch (Throwable e) {
            testContext.failNow(e);
          }
        });
  }

  @Test
  void testDeleteEnvironmentWithNoServiceDeployed(VertxTestContext testContext) {
    // Arrange
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    String environmentName = "env-delete-no-service";
    DeleteEnvironmentRequest deleteEnvironmentRequest =
        DeleteEnvironmentRequest.newBuilder().setEnvName(environmentName).build();

    ResponseState finalState = new DeleteEnvironment(true);
    EnvResponseAutomata envResponseAutomata = new EnvResponseAutomata(finalState, finalState);

    // Act

    RxEnvironmentServiceStub.deleteEnvironment(deleteEnvironmentRequest)
        .doOnNext(
            response -> {
              log.debug("Received response from deployer: {}", response);
              envResponseAutomata.switchState(response);
            })
        .subscribe(
            deleteEnvironmentResponse -> {
              if (envResponseAutomata.isComplete()) {
                testContext.completeNow();
              }
            },
            testContext::failNow);
  }

  @Test
  void testDeleteEnvironmentWithServicesStatusInvalid(VertxTestContext testContext) {
    // Arrange
    RxEnvironmentServiceGrpc.RxEnvironmentServiceStub RxEnvironmentServiceStub =
        RxEnvironmentServiceGrpc.newRxStub(channel);
    String environmentName = "env-delete-services-status-invalid";
    DeleteEnvironmentRequest deleteEnvironmentRequest =
        DeleteEnvironmentRequest.newBuilder().setEnvName(environmentName).build();
    // Act
    Flowable<DeleteEnvironmentResponse> responseSingle =
        RxEnvironmentServiceStub.deleteEnvironment(deleteEnvironmentRequest);

    // Assert
    responseSingle.subscribe(
        response -> {},
        err -> {
          try {
            assertThat(err.getClass()).isEqualTo(StatusRuntimeException.class);
            testContext.completeNow();
          } catch (Throwable e) {
            testContext.failNow(e);
          }
        });
  }
}
