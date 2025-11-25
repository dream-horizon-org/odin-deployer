package com.dream11.odin.grpc.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.Constants;
import com.dream11.odin.FailedComponent;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.error.OdinError;
import com.dream11.odin.injector.GuiceInjector;
import com.dream11.odin.responseautomata.ResponseState;
import com.dream11.odin.responseautomata.impl.ServiceResponseAutomata;
import com.dream11.odin.responseautomata.state.Deploy;
import com.dream11.odin.responseautomata.state.Operate;
import com.dream11.odin.responseautomata.state.UnDeploy;
import com.dream11.odin.responseautomata.state.Validate;
import com.dream11.odin.setup.Setup;
import com.dream11.odin.setup.TestChannelProvider;
import com.dream11.odin.util.ApplicationUtil;
import com.dream11.odin.util.SharedDataUtil;
import com.dream11.odin.util.TestUtil;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.inject.Guice;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Slf4j
@ExtendWith({VertxExtension.class, Setup.class})
@WireMockTest(httpPort = 9443)
@Timeout(value = 2, timeUnit = TimeUnit.MINUTES)
class ServiceServiceIT {
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
    FailedComponent.reset();
  }

  /***
   *  To test service deployment successful
   */
  @Test
  void testServiceDeploySuccessful(VertxTestContext testContext) {
    // Arrange
    String environmentName = Constants.TEST_ENV_NAME;
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);
    DeployServiceRequest request = TestUtil.getDeployServiceRequest(serviceName, environmentName);

    // Act
    Flowable<DeployServiceResponse> flowableResponse = RxServiceServiceStub.deployService(request);

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
                testContext.completeNow();
              }
            },
            testContext::failNow);
  }

  /***
   *  To test component deploy failed before service deploy failure
   */
  @Test
  void testServiceDeployComponentDeployFailed(VertxTestContext testContext) {
    // Arrange
    String environmentName = Constants.TEST_ENV_NAME;
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);
    String componentName = Constants.TEST_COMPONENT_NAME;
    DeployServiceRequest request =
        TestUtil.getDeployServiceRequest(
            serviceName,
            environmentName,
            Constants.TEST_CONFIG_KEY,
            Constants.TEST_PARAM_KEY,
            Constants.TEST_DEPLOYMENT_TYPE,
            componentName);
    FailedComponent.addFailedServiceComponent(
        Pair.of(serviceName, Action.DEPLOY), List.of(Pair.of(componentName, Action.DEPLOY)));

    ResponseState finalState = new Deploy(true, false);
    ResponseState initialState = new Validate(false, true, finalState);
    ServiceResponseAutomata responseAutomata =
        new ServiceResponseAutomata(initialState, finalState);

    // Act
    Flowable<DeployServiceResponse> flowableResponse = RxServiceServiceStub.deployService(request);

    // Assert
    flowableResponse
        .doOnNext(
            deployServiceResponse -> {
              log.debug(
                  String.format(
                      "Received message from deployer: %s", deployServiceResponse.toString()));
              responseAutomata.switchState(deployServiceResponse.getServiceResponse());
            })
        .subscribe(
            deployServiceResponse -> {
              if (responseAutomata.isComplete()) {
                testContext.completeNow();
              }
            },
            testContext::failNow);
  }

  /***
   *  To test service deploy with same configuration after deploy in progress
   */
  @Test
  void testServiceDeployResumeOnSameConfiguration(Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(
        __ -> {
          // Arrange
          GuiceInjector injector = new GuiceInjector(Guice.createInjector());
          SharedDataUtil.setInstance(vertx, injector);

          String environmentName = Constants.TEST_ENV_NAME;
          String serviceName =
              String.format(
                  "%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
          String componentName = Constants.TEST_COMPONENT_NAME;
          RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
              RxServiceServiceGrpc.newRxStub(channel);
          DeployServiceRequest request =
              TestUtil.getDeployServiceRequest(
                  serviceName,
                  environmentName,
                  Constants.TEST_CONFIG_KEY,
                  Constants.TEST_PARAM_KEY,
                  Constants.TEST_DEPLOYMENT_TYPE,
                  componentName);
          // Act
          // Deploy service in first attempt and disconnect
          Flowable<DeployServiceResponse> firstAttemptResponse =
              RxServiceServiceStub.deployService(request);

          // Deploy service second attempt and resume
          Flowable<DeployServiceResponse> secondAttemptResponse =
              RxServiceServiceStub.deployService(request);
          ServiceResponseAutomata deployResponseAutomata = new ServiceResponseAutomata();

          firstAttemptResponse
              .take(2L, TimeUnit.SECONDS) // Trigger second deploy after 1 second
              .doOnComplete(
                  () ->
                      secondAttemptResponse
                          .doOnNext(
                              deployServiceResponse -> {
                                log.debug(
                                    String.format(
                                        "Received message from deployer: %s",
                                        deployServiceResponse.toString()));
                                deployResponseAutomata.switchState(
                                    deployServiceResponse.getServiceResponse());
                              })
                          .subscribe(
                              r -> {
                                if (deployResponseAutomata.isComplete()) {
                                  testContext.completeNow();
                                }
                              },
                              testContext::failNow))
              .subscribe();
        });
  }

  /***
   *  To test service deploy with different configuration after deploy in progress
   */
  @Test
  void testServiceDeployResumeOnDifferentConfiguration(VertxTestContext testContext) {
    // Arrange
    String environmentName = Constants.TEST_ENV_NAME;
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);
    DeployServiceRequest request = TestUtil.getDeployServiceRequest(serviceName, environmentName);

    // Act
    // Deploy service and mark validate success in first attempt before disconnecting
    Flowable<DeployServiceResponse> flowableResponse = RxServiceServiceStub.deployService(request);

    DeployServiceRequest updatedRequest =
        TestUtil.getDeployServiceRequest(
            serviceName,
            environmentName,
            "updatedConfig",
            "param",
            Constants.TEST_DEPLOYMENT_TYPE,
            Constants.TEST_COMPONENT_NAME);

    Flowable<DeployServiceResponse> flowableResponseOnResume =
        RxServiceServiceStub.deployService(updatedRequest);

    flowableResponse
        .takeWhile(
            deployServiceResponse ->
                deployServiceResponse
                    .getServiceResponse()
                    .getServiceStatus()
                    .getServiceAction()
                    .equals(Action.VALIDATE.getName()))
        .doOnComplete(
            () ->
                flowableResponseOnResume.subscribe(
                    deployServiceResponse1 ->
                        testContext.failNow(
                            "Service deployment should have failed while deploy is already in progress with "
                                + "different configuration"),
                    err -> {
                      assertThat(err.getMessage())
                          .contains(OdinError.DUPLICATE_SERVICE_DEPLOYMENT.getErrorMessage());
                      testContext.completeNow();
                    }))
        .subscribe();
  }

  /***
   *  To test service deploy with same configuration after deploy fail
   */
  @Test
  void testServiceDeploySameConfigurationAfterDeployFailure(VertxTestContext testContext) {
    // Arrange
    String environmentName = Constants.TEST_ENV_NAME;
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);
    DeployServiceRequest request = TestUtil.getDeployServiceRequest(serviceName, environmentName);

    // Act
    // Deploy service failed in first attempt
    Flowable<DeployServiceResponse> flowableResponse = RxServiceServiceStub.deployService(request);
    FailedComponent.addFailedServiceComponent(
        Pair.of(serviceName, Action.DEPLOY),
        List.of(Pair.of(Constants.TEST_COMPONENT_NAME, Action.DEPLOY)));
    flowableResponse.blockingSubscribe();
    FailedComponent.reset();

    // Assert
    Flowable<DeployServiceResponse> resumeFlowableResponse =
        RxServiceServiceStub.deployService(request);
    ServiceResponseAutomata responseAutomata = new ServiceResponseAutomata();
    resumeFlowableResponse
        .doOnNext(
            deployServiceResponse -> {
              log.debug(
                  String.format(
                      "Received message from deployer: %s", deployServiceResponse.toString()));
              responseAutomata.switchState(deployServiceResponse.getServiceResponse());
            })
        .subscribe(
            deployServiceResponse -> {
              if (responseAutomata.isComplete()) {
                testContext.completeNow();
              }
            },
            testContext::failNow);
  }

  /***
   *  To test service deploy after deploy successful
   */
  @Test
  void testServiceDeployAfterDeploySuccess(VertxTestContext testContext) {
    // Arrange
    String environmentName = Constants.TEST_ENV_NAME;
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);
    DeployServiceRequest request = TestUtil.getDeployServiceRequest(serviceName, environmentName);

    // Insert service deployment success in service task
    TestUtil.createServiceTask(
        connection, serviceName, environmentName, TaskStatus.SUCCESSFUL, Action.DEPLOY);

    // Act
    Flowable<DeployServiceResponse> flowableResponse = RxServiceServiceStub.deployService(request);

    // Assert
    flowableResponse.subscribe(
        r -> testContext.failNow("Service deployment should have failed after deploy success"),
        err -> {
          // Assert
          assertThat(err.getMessage())
              .contains(
                  ExceptionUtil.getException(
                          OdinError.CANNOT_DEPLOY_OVER_ACTION_WITH_STATUS,
                          serviceName,
                          Action.DEPLOY,
                          TaskStatus.SUCCESSFUL,
                          com.dream11.odin.constant.Constants.USE_OPERATE)
                      .getMessage());
          testContext.completeNow();
        });
  }

  /***
   *  To test service deploy after un deploy successful
   */
  @Test
  void testServiceDeployAfterUnDeploySuccess(VertxTestContext testContext) {
    // Arrange
    String environmentName = Constants.TEST_ENV_NAME;
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());

    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);
    DeployServiceRequest request = TestUtil.getDeployServiceRequest(serviceName, environmentName);
    ServiceResponseAutomata responseAutomata = new ServiceResponseAutomata();
    TestUtil.createServiceTask(
        connection, serviceName, environmentName, TaskStatus.SUCCESSFUL, Action.UNDEPLOY);

    // Act
    Flowable<DeployServiceResponse> flowableResponse = RxServiceServiceStub.deployService(request);

    // Assert
    flowableResponse
        .doOnNext(
            deployServiceResponse -> {
              log.debug(
                  String.format(
                      "Received message from deployer: %s", deployServiceResponse.toString()));
              responseAutomata.switchState(deployServiceResponse.getServiceResponse());
            })
        .subscribe(
            deployServiceResponse -> {
              if (responseAutomata.isComplete()) {
                testContext.completeNow();
              }
            },
            testContext::failNow);
  }

  /***
   *  To test service deploy after un deploy failed
   */
  @Test
  void testServiceDeployAfterUnDeployFailed(VertxTestContext testContext) {
    // Arrange
    String environmentName = Constants.TEST_ENV_NAME;
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);
    DeployServiceRequest request = TestUtil.getDeployServiceRequest(serviceName, environmentName);

    // Insert service deployment success in service task
    TestUtil.createServiceTask(
        connection, serviceName, environmentName, TaskStatus.FAILED, Action.UNDEPLOY);

    // Act
    // Deploy service first attempt and skip first validate message processing
    Flowable<DeployServiceResponse> flowableResponse = RxServiceServiceStub.deployService(request);

    // Assert
    flowableResponse.subscribe(
        r -> testContext.failNow("Service deployment should have failed after un-deploy failed"),
        err -> {
          // Assert
          assertThat(err.getMessage())
              .contains(
                  ExceptionUtil.getException(
                          OdinError.CANNOT_DEPLOY_OVER_ACTION_WITH_STATUS,
                          serviceName,
                          Action.UNDEPLOY,
                          TaskStatus.FAILED,
                          com.dream11.odin.constant.Constants.UNDEPLOY_AGAIN)
                      .getMessage());
          testContext.completeNow();
        });
  }

  @Test
  void testServiceDeployAfterDeployFailedFollowedByStatusEnv(VertxTestContext testContext) {
    // Arrange
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    String envName = Constants.TEST_ENV_NAME;
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

    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);

    DeployServiceRequest request = TestUtil.getDeployServiceRequest(serviceName, envName);

    // Create service task
    int taskId =
        TestUtil.createServiceTask(
            connection, serviceName, envName, TaskStatus.FAILED, Action.DEPLOY);
    // Create component task
    TestUtil.createComponentTask(
        connection, taskId, componentName, componentConfig, TaskStatus.FAILED, Action.DEPLOY);

    // Create service task
    taskId =
        TestUtil.createServiceTask(
            connection, serviceName, envName, TaskStatus.FAILED, Action.HEALTHCHECK, 2);
    // Create component task
    TestUtil.createComponentTask(
        connection, taskId, componentName, componentConfig, TaskStatus.FAILED, Action.HEALTHCHECK);

    ServiceResponseAutomata responseAutomata = new ServiceResponseAutomata();

    // Act
    Flowable<DeployServiceResponse> flowableResponse = RxServiceServiceStub.deployService(request);

    // Assert
    flowableResponse
        .doOnNext(
            deployServiceResponse -> {
              log.debug(
                  String.format(
                      "Received message from deployer: %s", deployServiceResponse.toString()));
              responseAutomata.switchState(deployServiceResponse.getServiceResponse());
            })
        .subscribe(
            deployServiceResponse -> {
              if (responseAutomata.isComplete()) {
                testContext.completeNow();
              }
            },
            testContext::failNow);
  }

  /***
   *  To test service deploy after operate
   */
  @Test
  void testServiceDeployAfterOperate(VertxTestContext testContext) {
    // Arrange
    String environmentName = Constants.TEST_ENV_NAME;
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    RxServiceServiceGrpc.RxServiceServiceStub rxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);
    DeployServiceRequest request = TestUtil.getDeployServiceRequest(serviceName, environmentName);

    // Insert service deployment success in service task
    TestUtil.createServiceTask(
        connection, serviceName, environmentName, TaskStatus.SUCCESSFUL, Action.OPERATE);

    // Act
    // Deploy service first attempt and skip first validate message processing
    Flowable<DeployServiceResponse> flowableResponse = rxServiceServiceStub.deployService(request);

    // Assert
    flowableResponse.subscribe(
        r -> testContext.failNow("Service deployment should have failed after operate success"),
        err -> {
          // Assert
          assertThat(err.getMessage())
              .contains(
                  ExceptionUtil.getException(
                          OdinError.CANNOT_DEPLOY_OVER_ACTION_WITH_STATUS,
                          serviceName,
                          Action.OPERATE,
                          TaskStatus.SUCCESSFUL,
                          com.dream11.odin.constant.Constants.USE_OPERATE)
                      .getMessage());
          testContext.completeNow();
        });
  }

  /***
   *  To test service deploy with different configuration after deploy failure
   */
  @Test
  void testServiceDeployDifferentConfigurationAfterDeployFailure(
      Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(
        __ -> {
          // Arrange
          GuiceInjector injector = new GuiceInjector(Guice.createInjector());
          SharedDataUtil.setInstance(vertx, injector);

          String environmentName = Constants.TEST_ENV_NAME;
          String serviceName =
              String.format(
                  "%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
          String componentName = Constants.TEST_COMPONENT_NAME + "1";
          String configKey = Constants.TEST_CONFIG_KEY;
          String updatedConfigKey = "updatedConfig";
          String paramKey = Constants.TEST_PARAM_KEY;
          String deploymentType = Constants.TEST_DEPLOYMENT_TYPE;
          RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
              RxServiceServiceGrpc.newRxStub(channel);

          JsonObject oldComponentConfig =
              TestUtil.getComponentConfig(
                  componentName, configKey, paramKey, deploymentType, Map.of());
          JsonObject newComponentConfig =
              TestUtil.getComponentConfig(
                  componentName, updatedConfigKey, paramKey, deploymentType, Map.of());

          DeployServiceRequest request =
              TestUtil.getDeployServiceRequest(
                  serviceName,
                  environmentName,
                  updatedConfigKey,
                  paramKey,
                  deploymentType,
                  componentName);

          Integer taskId =
              TestUtil.createServiceTask(
                  connection, serviceName, environmentName, TaskStatus.FAILED, Action.DEPLOY);
          TestUtil.createComponentTask(
              connection,
              taskId,
              componentName,
              oldComponentConfig,
              TaskStatus.SUCCESSFUL,
              Action.DEPLOY);
          ServiceResponseAutomata responseAutomata = new ServiceResponseAutomata();

          // Act
          Flowable<DeployServiceResponse> flowableResponse =
              RxServiceServiceStub.deployService(request);

          // Assert
          flowableResponse.subscribe(
              deployServiceResponse -> {
                log.debug(
                    String.format(
                        "Received message from deployer: %s", deployServiceResponse.toString()));
                responseAutomata.switchState(deployServiceResponse.getServiceResponse());
                if (responseAutomata.isComplete()) {
                  TestUtil.assertComponentValidateTaskConfig(
                      connection, componentName, newComponentConfig);
                  TestUtil.assertComponentTaskConfig(connection, componentName, newComponentConfig);
                  TestUtil.assertLatestComponentTaskAction(
                      connection, componentName, Action.DEPLOY);
                  testContext.completeNow();
                }
              },
              testContext::failNow);
        });
  }

  /***
   *  To test service deploy with different odin configuration after deploy failure
   */
  @Test
  void testServiceDeployDifferentOdinConfigurationAfterDeployFailure(
      VertxTestContext testContext, Vertx vertx) {
    vertx.runOnContext(
        __ -> {
          // Arrange
          GuiceInjector injector = new GuiceInjector(Guice.createInjector());
          SharedDataUtil.setInstance(vertx, injector);

          String environmentName = Constants.TEST_ENV_NAME;
          String serviceName =
              String.format(
                  "%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
          String componentName = Constants.TEST_COMPONENT_NAME + "2";
          String configKey = Constants.TEST_CONFIG_KEY;
          String paramKey = Constants.TEST_PARAM_KEY;
          String deploymentType = Constants.TEST_DEPLOYMENT_TYPE;
          String updatedDeploymentType = "aws_test_ec2";
          RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
              RxServiceServiceGrpc.newRxStub(channel);

          JsonObject oldComponentConfig =
              TestUtil.getComponentConfig(
                  componentName, configKey, paramKey, deploymentType, Map.of());
          JsonObject newComponentConfig =
              TestUtil.getComponentConfig(
                  componentName, configKey, paramKey, updatedDeploymentType, Map.of());

          DeployServiceRequest request =
              TestUtil.getDeployServiceRequest(
                  serviceName,
                  environmentName,
                  configKey,
                  paramKey,
                  updatedDeploymentType,
                  componentName);

          Integer taskId =
              TestUtil.createServiceTask(
                  connection, serviceName, environmentName, TaskStatus.FAILED, Action.DEPLOY);
          TestUtil.createComponentTask(
              connection,
              taskId,
              componentName,
              oldComponentConfig,
              TaskStatus.SUCCESSFUL,
              Action.DEPLOY);
          ServiceResponseAutomata responseAutomata = new ServiceResponseAutomata();

          // Act
          Flowable<DeployServiceResponse> flowableResponse =
              RxServiceServiceStub.deployService(request);

          // Assert
          flowableResponse
              .doOnNext(
                  deployServiceResponse -> {
                    log.debug(
                        String.format(
                            "Received message from deployer: %s",
                            deployServiceResponse.toString()));
                    responseAutomata.switchState(deployServiceResponse.getServiceResponse());
                  })
              .subscribe(
                  deployServiceResponse -> {
                    if (responseAutomata.isComplete()) {
                      TestUtil.assertComponentValidateTaskConfig(
                          connection, componentName, newComponentConfig);
                      TestUtil.assertComponentTaskConfig(
                          connection, componentName, newComponentConfig);
                      TestUtil.assertUndeployDeployComponentTaskAction(connection, componentName);
                      testContext.completeNow();
                    }
                  },
                  testContext::failNow);
        });
  }

  /***
   *  To test service deploy with removed component after deploy failure
   */
  @Test
  void testServiceDeployRemovedComponentAfterDeployFailure(
      Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(
        __ -> {
          // Arrange
          GuiceInjector injector = new GuiceInjector(Guice.createInjector());
          SharedDataUtil.setInstance(vertx, injector);

          String environmentName = Constants.TEST_ENV_NAME;
          String serviceName =
              String.format(
                  "%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
          String oldComponentName = Constants.TEST_COMPONENT_NAME + "3";
          String newComponentName = String.format("%s3-new", Constants.TEST_COMPONENT_NAME);
          String configKey = Constants.TEST_CONFIG_KEY;
          String paramKey = Constants.TEST_PARAM_KEY;
          String deploymentType = Constants.TEST_DEPLOYMENT_TYPE;
          RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
              RxServiceServiceGrpc.newRxStub(channel);

          JsonObject oldComponentConfig =
              TestUtil.getComponentConfig(
                  oldComponentName, configKey, paramKey, deploymentType, Map.of());
          JsonObject newComponentConfig =
              TestUtil.getComponentConfig(
                  newComponentName, configKey, paramKey, deploymentType, Map.of());

          DeployServiceRequest request =
              TestUtil.getDeployServiceRequest(
                  serviceName,
                  environmentName,
                  configKey,
                  paramKey,
                  deploymentType,
                  newComponentName);

          Integer taskId =
              TestUtil.createServiceTask(
                  connection, serviceName, environmentName, TaskStatus.FAILED, Action.DEPLOY);
          TestUtil.createComponentTask(
              connection,
              taskId,
              oldComponentName,
              oldComponentConfig,
              TaskStatus.SUCCESSFUL,
              Action.DEPLOY);
          ServiceResponseAutomata responseAutomata = new ServiceResponseAutomata();

          // Act
          Flowable<DeployServiceResponse> flowableResponse =
              RxServiceServiceStub.deployService(request);

          // Assert
          flowableResponse
              .doOnNext(
                  deployServiceResponse -> {
                    log.debug(
                        String.format(
                            "Received message from deployer: %s",
                            deployServiceResponse.toString()));
                    responseAutomata.switchState(deployServiceResponse.getServiceResponse());
                  })
              .subscribe(
                  deployServiceResponse -> {
                    if (responseAutomata.isComplete()) {
                      TestUtil.assertComponentValidateTaskConfig(
                          connection, newComponentName, newComponentConfig);
                      TestUtil.assertComponentTaskConfig(
                          connection, newComponentName, newComponentConfig);
                      TestUtil.assertComponentTaskConfig(
                          connection, oldComponentName, oldComponentConfig);
                      TestUtil.assertLatestComponentTaskAction(
                          connection, oldComponentName, Action.UNDEPLOY);
                      TestUtil.assertLatestComponentTaskAction(
                          connection, newComponentName, Action.DEPLOY);
                      testContext.completeNow();
                    }
                  },
                  testContext::failNow);
        });
  }

  /***
   *  To test service deploy with successfully deployed component after deploy failure
   */
  @Test
  void testServiceDeploySkipComponentAfterDeployFailure(Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(
        __ -> {
          // Arrange
          GuiceInjector injector = new GuiceInjector(Guice.createInjector());
          SharedDataUtil.setInstance(vertx, injector);

          String environmentName = Constants.TEST_ENV_NAME;
          String serviceName =
              String.format(
                  "%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
          String componentName1 = String.format("%s-1", Constants.TEST_COMPONENT_NAME);
          String componentName2 = String.format("%s-2", Constants.TEST_COMPONENT_NAME);
          String configKey = Constants.TEST_CONFIG_KEY;
          String paramKey = Constants.TEST_PARAM_KEY;
          String deploymentType = Constants.TEST_DEPLOYMENT_TYPE;
          RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
              RxServiceServiceGrpc.newRxStub(channel);

          JsonObject componentConfig1 =
              TestUtil.getComponentConfig(
                  componentName1, configKey, paramKey, deploymentType, Map.of());
          JsonObject componentConfig2 =
              TestUtil.getComponentConfig(
                  componentName2, configKey, paramKey, deploymentType, Map.of());

          DeployServiceRequest request =
              TestUtil.getDeployServiceRequest(
                  serviceName,
                  environmentName,
                  configKey,
                  paramKey,
                  deploymentType,
                  componentName1,
                  componentName2);

          Integer taskId =
              TestUtil.createServiceTask(
                  connection, serviceName, environmentName, TaskStatus.FAILED, Action.DEPLOY);
          TestUtil.createComponentTask(
              connection,
              taskId,
              componentName1,
              componentConfig1,
              TaskStatus.SUCCESSFUL,
              Action.DEPLOY);
          TestUtil.createComponentTask(
              connection,
              taskId,
              componentName2,
              componentConfig2,
              TaskStatus.FAILED,
              Action.DEPLOY);
          ServiceResponseAutomata responseAutomata = new ServiceResponseAutomata();

          // Act
          Flowable<DeployServiceResponse> flowableResponse =
              RxServiceServiceStub.deployService(request);

          // Assert
          flowableResponse
              .doOnNext(
                  deployServiceResponse -> {
                    log.debug(
                        String.format(
                            "Received message from deployer: %s",
                            deployServiceResponse.toString()));
                    // Component1 should be successful from beginning
                    if (deployServiceResponse
                        .getServiceResponse()
                        .getServiceStatus()
                        .getServiceAction()
                        .equals(Action.DEPLOY.getName())) {
                      TestUtil.assertComponentTaskStatus(
                          connection, componentName1, Action.DEPLOY, TaskStatus.SUCCESSFUL);
                    }
                    responseAutomata.switchState(deployServiceResponse.getServiceResponse());
                  })
              .subscribe(
                  deployServiceResponse -> {
                    if (responseAutomata.isComplete()) {
                      TestUtil.assertComponentTaskStatus(
                          connection, componentName1, Action.DEPLOY, TaskStatus.SUCCESSFUL);
                      TestUtil.assertComponentTaskStatus(
                          connection, componentName2, Action.DEPLOY, TaskStatus.SUCCESSFUL);
                      TestUtil.assertLatestComponentTaskAction(
                          connection, componentName1, Action.DEPLOY);
                      TestUtil.assertLatestComponentTaskAction(
                          connection, componentName2, Action.DEPLOY);
                      testContext.completeNow();
                    }
                  },
                  testContext::failNow);
        });
  }

  /***
   *  To test service deploy with un triggered component (unable to trigger due to failure of dependent component) after deploy failure
   */
  @Test
  void testServiceDeployUnTriggeredComponentAfterDeployFailure(
      Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(
        __ -> {
          // Arrange
          GuiceInjector injector = new GuiceInjector(Guice.createInjector());
          SharedDataUtil.setInstance(vertx, injector);

          String environmentName = Constants.TEST_ENV_NAME;
          String serviceName =
              String.format(
                  "%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
          String componentName1 = String.format("%s-1", Constants.TEST_COMPONENT_NAME);
          String componentName2 = String.format("%s-2", Constants.TEST_COMPONENT_NAME);
          String configKey = Constants.TEST_CONFIG_KEY;
          String paramKey = Constants.TEST_PARAM_KEY;
          String deploymentType = Constants.TEST_DEPLOYMENT_TYPE;
          RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
              RxServiceServiceGrpc.newRxStub(channel);

          JsonObject componentConfig1 =
              TestUtil.getComponentConfig(
                  componentName1, configKey, paramKey, deploymentType, Map.of());
          JsonObject componentConfig2 =
              TestUtil.getComponentConfigWithDependsOn(
                  componentName2, configKey, paramKey, deploymentType, componentName1);

          DeployServiceRequest request =
              TestUtil.getDeployServiceRequest(
                  serviceName,
                  environmentName,
                  configKey,
                  paramKey,
                  deploymentType,
                  componentName1,
                  componentName2);

          Integer taskId =
              TestUtil.createServiceTask(
                  connection, serviceName, environmentName, TaskStatus.FAILED, Action.DEPLOY);
          TestUtil.createComponentTask(
              connection,
              taskId,
              componentName1,
              componentConfig1,
              TaskStatus.FAILED,
              Action.DEPLOY);
          TestUtil.createComponentTask(
              connection,
              taskId,
              componentName2,
              componentConfig2,
              TaskStatus.FAILED,
              Action.DEPLOY);
          ServiceResponseAutomata responseAutomata = new ServiceResponseAutomata();

          // Act
          Flowable<DeployServiceResponse> flowableResponse =
              RxServiceServiceStub.deployService(request);

          // Assert
          flowableResponse
              .doOnNext(
                  deployServiceResponse -> {
                    log.debug(
                        String.format(
                            "Received message from deployer: %s",
                            deployServiceResponse.toString()));
                    responseAutomata.switchState(deployServiceResponse.getServiceResponse());
                  })
              .subscribe(
                  deployServiceResponse -> {
                    if (responseAutomata.isComplete()) {
                      TestUtil.assertComponentTaskStatus(
                          connection, componentName1, Action.DEPLOY, TaskStatus.SUCCESSFUL);
                      TestUtil.assertComponentTaskStatus(
                          connection, componentName2, Action.DEPLOY, TaskStatus.SUCCESSFUL);
                      TestUtil.assertLatestComponentTaskAction(
                          connection, componentName1, Action.DEPLOY);
                      TestUtil.assertLatestComponentTaskAction(
                          connection, componentName2, Action.DEPLOY);
                      testContext.completeNow();
                    }
                  },
                  testContext::failNow);
        });
  }

  /***
   *  To test service deploy with component deployment changed and undeploy failed for old component flavour
   */
  @Test
  void testServiceDeployComponentOdinConfigChangeUnDeployFailure(
      Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(
        __ -> {
          // Arrange
          GuiceInjector injector = new GuiceInjector(Guice.createInjector());
          SharedDataUtil.setInstance(vertx, injector);
          String environmentName = Constants.TEST_ENV_NAME;
          String serviceName =
              String.format(
                  "%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
          String componentName = Constants.TEST_COMPONENT_NAME;
          String configKey = Constants.TEST_CONFIG_KEY;
          String paramKey = Constants.TEST_PARAM_KEY;
          String deploymentType = Constants.TEST_DEPLOYMENT_TYPE;
          RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
              RxServiceServiceGrpc.newRxStub(channel);

          JsonObject componentConfig =
              TestUtil.getComponentConfig(
                  componentName, configKey, paramKey, deploymentType, Map.of());

          DeployServiceRequest request =
              TestUtil.getDeployServiceRequest(
                  serviceName, environmentName, configKey, paramKey, deploymentType, componentName);

          Integer taskId =
              TestUtil.createServiceTask(
                  connection, serviceName, environmentName, TaskStatus.FAILED, Action.DEPLOY);
          TestUtil.createComponentTask(
              connection,
              taskId,
              componentName,
              componentConfig,
              TaskStatus.FAILED,
              Action.UNDEPLOY);
          TestUtil.createComponentTask(
              connection, taskId, componentName, componentConfig, TaskStatus.FAILED, Action.DEPLOY);
          ServiceResponseAutomata responseAutomata = new ServiceResponseAutomata();

          // Act
          Flowable<DeployServiceResponse> flowableResponse =
              RxServiceServiceStub.deployService(request);

          // Assert
          flowableResponse
              .doOnNext(
                  deployServiceResponse -> {
                    log.debug(
                        String.format(
                            "Received message from deployer: %s",
                            deployServiceResponse.toString()));
                    responseAutomata.switchState(deployServiceResponse.getServiceResponse());
                  })
              .subscribe(
                  deployServiceResponse -> {
                    if (responseAutomata.isComplete()) {
                      TestUtil.assertComponentTaskStatus(
                          connection, componentName, Action.DEPLOY, TaskStatus.SUCCESSFUL);
                      TestUtil.assertComponentTaskStatus(
                          connection, componentName, Action.UNDEPLOY, TaskStatus.SUCCESSFUL);
                      TestUtil.assertLatestComponentTaskAction(
                          connection, componentName, Action.DEPLOY);
                      testContext.completeNow();
                    }
                  },
                  testContext::failNow);
        });
  }

  /***
   *  To test service deploy with component deployment changed and undeploy successful for old component flavour
   */
  @Test
  void testServiceDeployComponentOdinConfigChangeUnDeploySuccess(
      Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(
        __ -> {
          // Arrange
          GuiceInjector injector = new GuiceInjector(Guice.createInjector());
          SharedDataUtil.setInstance(vertx, injector);

          String environmentName = Constants.TEST_ENV_NAME;
          String serviceName =
              String.format(
                  "%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
          String componentName = Constants.TEST_COMPONENT_NAME;
          String configKey = Constants.TEST_CONFIG_KEY;
          String paramKey = Constants.TEST_PARAM_KEY;
          String deploymentType = Constants.TEST_DEPLOYMENT_TYPE;
          RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
              RxServiceServiceGrpc.newRxStub(channel);

          JsonObject componentConfig1 =
              TestUtil.getComponentConfig(
                  componentName, configKey, paramKey, deploymentType, Map.of());

          DeployServiceRequest request =
              TestUtil.getDeployServiceRequest(
                  serviceName, environmentName, configKey, paramKey, deploymentType, componentName);

          Integer taskId =
              TestUtil.createServiceTask(
                  connection, serviceName, environmentName, TaskStatus.FAILED, Action.DEPLOY);
          TestUtil.createComponentTask(
              connection,
              taskId,
              componentName,
              componentConfig1,
              TaskStatus.SUCCESSFUL,
              Action.UNDEPLOY);
          TestUtil.createComponentTask(
              connection,
              taskId,
              componentName,
              componentConfig1,
              TaskStatus.FAILED,
              Action.DEPLOY);
          ServiceResponseAutomata responseAutomata = new ServiceResponseAutomata();

          // Act
          Flowable<DeployServiceResponse> flowableResponse =
              RxServiceServiceStub.deployService(request);

          // Assert
          flowableResponse
              .doOnNext(
                  deployServiceResponse -> {
                    log.debug(
                        String.format(
                            "Received message from deployer: %s",
                            deployServiceResponse.toString()));
                    responseAutomata.switchState(deployServiceResponse.getServiceResponse());
                  })
              .subscribe(
                  deployServiceResponse -> {
                    if (responseAutomata.isComplete()) {
                      TestUtil.assertComponentTaskStatus(
                          connection, componentName, Action.DEPLOY, TaskStatus.SUCCESSFUL);
                      TestUtil.assertLatestComponentTaskAction(
                          connection, componentName, Action.DEPLOY);
                      testContext.completeNow();
                    }
                  },
                  testContext::failNow);
        });
  }

  @Test
  void testOperateServiceEnvDoesNotExist(VertxTestContext testContext)
      throws InvalidProtocolBufferException {
    // Arrange
    String name = "odin-operate-env-doesnt-exist";
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);

    Struct.Builder configBuilder = Struct.newBuilder();
    String configJson =
        """
                        {
                           "component_definition":  [{
                              "type": "application",
                              "name": "odindemo5",
                              "version": "1.0.0",
                              "config": {
                                "artifact": {
                                  "name": "odindemo",
                                  "version": "1.1.1"
                                }
                              }
                            }],
                            "provisioning_config":[{
                              "component_name": "odindemo5",
                            "deployment_type": "aws_ec2"
                            }]
                        }""";

    com.google.gson.JsonObject jsonObject = JsonParser.parseString(configJson).getAsJsonObject();
    JsonFormat.parser().merge(new Gson().toJson(jsonObject), configBuilder);

    OperateServiceRequest request =
        OperateServiceRequest.newBuilder()
            .setServiceName(name)
            .setEnvName(name)
            .setIsComponentOperation(false)
            .setOperation(Constants.SERVICE_OPERATION_ADD_COMPONENT)
            .setConfig(configBuilder.build())
            .build();

    // Act
    Flowable<OperateServiceResponse> flowableResponse =
        RxServiceServiceStub.operateService(request);

    // Assert
    flowableResponse.subscribe(
        operateServiceResponse -> testContext.failNow("Should have thrown exception"),
        err -> {
          assertThat(err).isInstanceOf(StatusRuntimeException.class);
          assertThat(err).hasMessageContaining("Environment with name:" + name + " does not exist");
          testContext.completeNow();
        });
  }

  @Test
  void testOperateServiceDoesNotExist(VertxTestContext testContext)
      throws InvalidProtocolBufferException {
    // Arrange
    String name = "odin-operate-service-doesnt-exist";
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);

    Struct.Builder configBuilder = Struct.newBuilder();
    String componentName = "odindemo5";
    String configJson =
        """
                        {
                           "component_definition":  [{
                              "type": "application",
                              "name": "%s",
                              "version": "1.0.0",
                              "config": {
                                "artifact": {
                                  "name": "cj-frontend-v1",
                                  "version": "1.1.1"
                                }
                              }
                            }],
                            "provisioning_config":[{
                              "component_name": "%s",
                              "deployment_type": "aws_ec2"
                            }]
                        }
                        """
            .formatted(componentName, componentName);
    com.google.gson.JsonObject jsonObject = JsonParser.parseString(configJson).getAsJsonObject();
    JsonFormat.parser().merge(new Gson().toJson(jsonObject), configBuilder);

    OperateServiceRequest request =
        OperateServiceRequest.newBuilder()
            .setServiceName(name)
            .setEnvName(Constants.TEST_ENV_NAME)
            .setIsComponentOperation(false)
            .setOperation(Constants.SERVICE_OPERATION_ADD_COMPONENT)
            .setConfig(configBuilder.build())
            .build();

    // Act
    Flowable<OperateServiceResponse> flowableResponse =
        RxServiceServiceStub.operateService(request);

    // Assert
    flowableResponse.subscribe(
        operateServiceResponse -> testContext.failNow("Should have thrown exception"),
        err -> {
          assertThat(err).isInstanceOf(StatusRuntimeException.class);
          assertThat(err).hasMessageContaining("Service with name:" + name + " does not exist");
          testContext.completeNow();
        });
  }

  @Test
  void testOperateComponentDoesntExist(VertxTestContext testContext)
      throws InvalidProtocolBufferException {
    // Arrange
    String name = "odin-operate-component-doesnt-exist";
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);

    Struct.Builder configBuilder = Struct.newBuilder();
    String configJson = "{}";

    com.google.gson.JsonObject jsonObject = JsonParser.parseString(configJson).getAsJsonObject();
    JsonFormat.parser().merge(new Gson().toJson(jsonObject), configBuilder);

    OperateServiceRequest request =
        OperateServiceRequest.newBuilder()
            .setServiceName(name)
            .setEnvName(name)
            .setIsComponentOperation(true)
            .setComponentName("component1")
            .setOperation("component-operation")
            .setConfig(configBuilder.build())
            .build();

    // Act
    Flowable<OperateServiceResponse> flowableResponse =
        RxServiceServiceStub.operateService(request);

    // Assert
    flowableResponse.subscribe(
        operateServiceResponse -> testContext.failNow("Should have thrown exception"),
        err -> {
          assertThat(err).isInstanceOf(StatusRuntimeException.class);
          assertThat(err)
              .hasMessageContaining(
                  " Component component1 cannot be operated in state undeploy with status SUCCESSFUL");
          testContext.completeNow();
        });
  }

  @Test
  void testOperateComponentWhenPreviousDeploymentFailed(VertxTestContext testContext)
      throws InvalidProtocolBufferException {
    // Arrange
    String name = "odin-operate-component-prev-deploy-failed";
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);

    Struct.Builder configBuilder = Struct.newBuilder();
    String configJson = "{}";

    com.google.gson.JsonObject jsonObject = JsonParser.parseString(configJson).getAsJsonObject();
    JsonFormat.parser().merge(new Gson().toJson(jsonObject), configBuilder);

    OperateServiceRequest request =
        OperateServiceRequest.newBuilder()
            .setServiceName(name)
            .setEnvName(name)
            .setIsComponentOperation(true)
            .setComponentName("odindemo5")
            .setOperation("component-operation")
            .setConfig(configBuilder.build())
            .build();

    // Act
    Flowable<OperateServiceResponse> flowableResponse =
        RxServiceServiceStub.operateService(request);

    // Assert
    flowableResponse.subscribe(
        operateServiceResponse -> testContext.failNow("Should have thrown exception"),
        err -> {
          assertThat(err).isInstanceOf(StatusRuntimeException.class);
          assertThat(err)
              .hasMessageContaining(
                  "Service odin-operate-component-prev-deploy-failed cannot be operated in state deploy with status FAILED");
          testContext.completeNow();
        });
  }

  @Test
  void testOperateComponentWhenPreviousUndeploySuccessful(
      Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(
        __ -> {
          // Arrange
          String name = "odin-operate-component-prev-undeploy-successful";
          RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
              RxServiceServiceGrpc.newRxStub(channel);

          Struct.Builder configBuilder = Struct.newBuilder();
          String configJson = "{}";

          com.google.gson.JsonObject jsonObject =
              JsonParser.parseString(configJson).getAsJsonObject();
          try {
            JsonFormat.parser().merge(new Gson().toJson(jsonObject), configBuilder);
          } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
          }

          OperateServiceRequest request =
              OperateServiceRequest.newBuilder()
                  .setServiceName(name)
                  .setEnvName(name)
                  .setIsComponentOperation(true)
                  .setComponentName("odindemo5")
                  .setOperation("component-operation")
                  .setConfig(configBuilder.build())
                  .build();

          // Act
          Flowable<OperateServiceResponse> flowableResponse =
              RxServiceServiceStub.operateService(request);

          // Assert
          flowableResponse.subscribe(
              operateServiceResponse -> testContext.failNow("Should have thrown exception"),
              err -> {
                assertThat(err).isInstanceOf(StatusRuntimeException.class);
                assertThat(err)
                    .hasMessageContaining(
                        "Service "
                            + name
                            + " cannot be operated in state undeploy with status SUCCESSFUL");
                testContext.completeNow();
              });
        });
  }

  @Test
  void testOperateServiceAddNonApplicationComponent(VertxTestContext testContext)
      throws InvalidProtocolBufferException {
    // Arrange
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    String envName = Constants.TEST_ENV_NAME;
    String componentName1 =
        String.format(
            "%s-%d", Constants.TEST_COMPONENT_NAME, ApplicationUtil.generateIntegerUUID());
    String componentName2 =
        String.format(
            "%s-%d", Constants.TEST_COMPONENT_NAME, ApplicationUtil.generateIntegerUUID());
    JsonObject component1Config =
        TestUtil.getComponentConfig(
            componentName1,
            Constants.TEST_CONFIG_KEY,
            Constants.TEST_PARAM_KEY,
            "container",
            Map.of());
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);

    // Create service task
    int taskId =
        TestUtil.createServiceTask(
            connection, serviceName, envName, TaskStatus.SUCCESSFUL, Action.DEPLOY);
    // Create component task
    TestUtil.createComponentTask(
        connection, taskId, componentName1, component1Config, TaskStatus.SUCCESSFUL, Action.DEPLOY);

    Struct.Builder configBuilder = Struct.newBuilder();
    String configJson =
        """
                        {
                           "component_definition":  [{
                              "type": "application",
                              "name": "%s",
                              "version": "1.0.0",
                              "config": {
                                "artifact": {
                                  "name": "cj-frontend-v1",
                                  "version": "1.1.1"
                                }
                              }
                            }],
                            "provisioning_config":[{
                              "component_name": "%s",
                              "deployment_type": "aws_ec2"
                            }]
                        }
                        """
            .formatted(componentName2, componentName2);

    com.google.gson.JsonObject jsonObject = JsonParser.parseString(configJson).getAsJsonObject();
    JsonFormat.parser().merge(new Gson().toJson(jsonObject), configBuilder);

    OperateServiceRequest request =
        OperateServiceRequest.newBuilder()
            .setServiceName(serviceName)
            .setEnvName(envName)
            .setIsComponentOperation(false)
            .setOperation(Constants.SERVICE_OPERATION_ADD_COMPONENT)
            .setConfig(configBuilder.build())
            .build();

    // Act
    Flowable<OperateServiceResponse> flowableResponse =
        RxServiceServiceStub.operateService(request);

    ResponseState finalState = new Operate(true, true);
    ResponseState initialState = new Validate(false, true, finalState);

    ServiceResponseAutomata responseAutomata =
        new ServiceResponseAutomata(initialState, finalState);

    // Assert
    flowableResponse
        .doOnNext(
            operateServiceResponse -> {
              log.debug(
                  String.format(
                      "Received message from deployer: %s", operateServiceResponse.toString()));
              responseAutomata.switchState(operateServiceResponse.getServiceResponse());
            })
        .subscribe(
            operateServiceResponse -> {
              if (responseAutomata.isComplete()) {
                // Check component deploy task
                TestUtil.assertComponentTaskStatus(
                    connection, componentName1, Action.DEPLOY, TaskStatus.SUCCESSFUL);
                TestUtil.assertComponentTaskStatus(
                    connection, componentName2, Action.DEPLOY, TaskStatus.SUCCESSFUL);
                testContext.completeNow();
              }
            },
            testContext::failNow);
  }

  @Test
  void testOperateServiceAddComponent(VertxTestContext testContext)
      throws InvalidProtocolBufferException {
    // Arrange
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    String envName = Constants.TEST_ENV_NAME;
    String componentName1 =
        String.format(
            "%s-%d", Constants.TEST_COMPONENT_NAME, ApplicationUtil.generateIntegerUUID());
    String componentName2 =
        String.format(
            "%s-%d", Constants.TEST_COMPONENT_NAME, ApplicationUtil.generateIntegerUUID());
    JsonObject component1Config =
        TestUtil.getComponentConfig(
            componentName1,
            Constants.TEST_CONFIG_KEY,
            Constants.TEST_PARAM_KEY,
            "container",
            Map.of());
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);

    // Create service task
    int taskId =
        TestUtil.createServiceTask(
            connection, serviceName, envName, TaskStatus.SUCCESSFUL, Action.DEPLOY);
    // Create component task
    TestUtil.createComponentTask(
        connection, taskId, componentName1, component1Config, TaskStatus.SUCCESSFUL, Action.DEPLOY);

    Struct.Builder configBuilder = Struct.newBuilder();
    String configJson =
        """
                        {
                           "component_definition":  [{
                              "type": "application",
                              "name": "%s",
                              "version": "1.0.0",
                              "config": {
                                "artifact": {
                                  "name": "cj-frontend-v1",
                                  "version": "1.1.1"
                                }
                              }
                            }],
                            "provisioning_config":[{
                              "component_name": "%s",
                              "deployment_type": "aws_ec2"
                            }]
                        }
                        """
            .formatted(componentName2, componentName2);

    com.google.gson.JsonObject jsonObject = JsonParser.parseString(configJson).getAsJsonObject();
    JsonFormat.parser().merge(new Gson().toJson(jsonObject), configBuilder);

    OperateServiceRequest request =
        OperateServiceRequest.newBuilder()
            .setServiceName(serviceName)
            .setEnvName(envName)
            .setIsComponentOperation(false)
            .setOperation(Constants.SERVICE_OPERATION_ADD_COMPONENT)
            .setConfig(configBuilder.build())
            .build();

    // Act
    Flowable<OperateServiceResponse> flowableResponse =
        RxServiceServiceStub.operateService(request);

    ResponseState finalState = new Operate(true, true);
    ResponseState initialState = new Validate(false, true, finalState);

    ServiceResponseAutomata responseAutomata =
        new ServiceResponseAutomata(initialState, finalState);

    // Assert
    flowableResponse
        .doOnNext(
            operateServiceResponse -> {
              log.debug(
                  String.format(
                      "Received message from deployer: %s", operateServiceResponse.toString()));
              responseAutomata.switchState(operateServiceResponse.getServiceResponse());
            })
        .subscribe(
            operateServiceResponse -> {
              if (responseAutomata.isComplete()) {
                // Check component deploy task
                TestUtil.assertComponentTaskStatus(
                    connection, componentName1, Action.DEPLOY, TaskStatus.SUCCESSFUL);
                TestUtil.assertComponentTaskStatus(
                    connection, componentName2, Action.DEPLOY, TaskStatus.SUCCESSFUL);
                testContext.completeNow();
              }
            },
            testContext::failNow);
  }

  @Test
  void testOperateServiceAddComponentResumeAfterDisconnect(VertxTestContext testContext)
      throws InvalidProtocolBufferException {
    // Arrange
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    String envName = Constants.TEST_ENV_NAME;
    String componentName1 =
        String.format(
            "%s-%d", Constants.TEST_COMPONENT_NAME, ApplicationUtil.generateIntegerUUID());
    String componentName2 =
        String.format(
            "%s-%d", Constants.TEST_COMPONENT_NAME, ApplicationUtil.generateIntegerUUID());
    JsonObject component1Config =
        TestUtil.getComponentConfig(
            componentName1,
            Constants.TEST_CONFIG_KEY,
            Constants.TEST_PARAM_KEY,
            "container",
            Map.of());
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);

    // Create service task
    int taskId =
        TestUtil.createServiceTask(
            connection, serviceName, envName, TaskStatus.SUCCESSFUL, Action.DEPLOY);
    // Create component task
    TestUtil.createComponentTask(
        connection, taskId, componentName1, component1Config, TaskStatus.SUCCESSFUL, Action.DEPLOY);

    Struct.Builder configBuilder = Struct.newBuilder();
    String configJson =
        """
                        {
                           "component_definition":  [{
                              "type": "application",
                              "name": "%s",
                              "version": "1.0.0",
                              "config": {
                                "artifact": {
                                  "name": "cj-frontend-v1",
                                  "version": "1.1.1"
                                }
                              }
                            }],
                            "provisioning_config":[{
                              "component_name": "%s",
                              "deployment_type": "aws_ec2"
                            }]
                        }
                        """
            .formatted(componentName2, componentName2);

    com.google.gson.JsonObject jsonObject = JsonParser.parseString(configJson).getAsJsonObject();
    JsonFormat.parser().merge(new Gson().toJson(jsonObject), configBuilder);

    OperateServiceRequest request =
        OperateServiceRequest.newBuilder()
            .setServiceName(serviceName)
            .setEnvName(envName)
            .setIsComponentOperation(false)
            .setOperation(Constants.SERVICE_OPERATION_ADD_COMPONENT)
            .setConfig(configBuilder.build())
            .build();

    // Act
    Flowable<OperateServiceResponse> flowableResponse =
        RxServiceServiceStub.operateService(request);

    ResponseState finalState = new Operate(true, true);
    ResponseState initialState = new Validate(false, true, finalState);

    ServiceResponseAutomata responseAutomata =
        new ServiceResponseAutomata(initialState, finalState);

    ServiceResponseAutomata responseAutomataNew =
        new ServiceResponseAutomata(finalState, finalState);
    Flowable<OperateServiceResponse> operateServiceResponseFlowableInProgress =
        RxServiceServiceStub.operateService(request)
            .doOnNext(
                operateServiceResponse -> {
                  log.debug(
                      String.format(
                          "Received message from deployer: %s", operateServiceResponse.toString()));
                  responseAutomataNew.switchState(operateServiceResponse.getServiceResponse());
                  TestUtil.updateServiceStatus(
                      connection, taskId + 1, TaskStatus.IN_PROGRESS, TaskStatus.SUCCESSFUL);
                });

    // Assert
    flowableResponse
        .takeWhile(
            deployServiceResponse ->
                !(TaskStatus.IN_PROGRESS
                        .getValue()
                        .equals(
                            deployServiceResponse
                                .getServiceResponse()
                                .getServiceStatus()
                                .getServiceStatus())
                    && Action.OPERATE
                        .getName()
                        .equals(
                            deployServiceResponse
                                .getServiceResponse()
                                .getServiceStatus()
                                .getServiceAction())))
        .doOnComplete(
            () -> {
              log.debug("Resuming after disconnect");
              TestUtil.updateServiceStatus(
                  connection, taskId + 1, TaskStatus.SUCCESSFUL, TaskStatus.IN_PROGRESS);
            })
        .concatWith(operateServiceResponseFlowableInProgress)
        .doOnNext(
            operateServiceResponse -> {
              log.debug(
                  String.format(
                      "Received message from deployer: %s", operateServiceResponse.toString()));
              responseAutomata.switchState(operateServiceResponse.getServiceResponse());
            })
        .subscribe(
            operateServiceResponse -> {
              if (responseAutomataNew.isComplete()) {
                // Check component deploy task
                TestUtil.assertComponentTaskStatus(
                    connection, componentName1, Action.DEPLOY, TaskStatus.SUCCESSFUL);
                TestUtil.assertComponentTaskStatus(
                    connection, componentName2, Action.DEPLOY, TaskStatus.SUCCESSFUL);
                testContext.completeNow();
              }
            },
            testContext::failNow);
  }

  @Test
  void testOperateServiceRemoveComponent(VertxTestContext testContext)
      throws InvalidProtocolBufferException {
    // Arrange
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    String envName = Constants.TEST_ENV_NAME;
    String componentName1 =
        String.format(
            "%s-%d", Constants.TEST_COMPONENT_NAME, ApplicationUtil.generateIntegerUUID());
    String componentName2 =
        String.format(
            "%s-%d", Constants.TEST_COMPONENT_NAME, ApplicationUtil.generateIntegerUUID());
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);

    Struct.Builder configBuilder = Struct.newBuilder();
    String configJson = "{\"component_name\":\"" + componentName1 + "\"}";
    JsonObject component1Config =
        TestUtil.getComponentConfig(
            componentName1,
            Constants.TEST_CONFIG_KEY,
            Constants.TEST_PARAM_KEY,
            "container",
            Map.of());
    JsonObject component2Config =
        TestUtil.getComponentConfig(
            componentName2,
            Constants.TEST_CONFIG_KEY,
            Constants.TEST_PARAM_KEY,
            "container",
            Map.of());

    com.google.gson.JsonObject jsonObject = JsonParser.parseString(configJson).getAsJsonObject();
    JsonFormat.parser().merge(new Gson().toJson(jsonObject), configBuilder);

    // Create service task
    Integer taskId =
        TestUtil.createServiceTask(
            connection, serviceName, envName, TaskStatus.SUCCESSFUL, Action.DEPLOY);
    // Create component task
    TestUtil.createComponentTask(
        connection, taskId, componentName1, component1Config, TaskStatus.SUCCESSFUL, Action.DEPLOY);
    TestUtil.createComponentTask(
        connection, taskId, componentName2, component2Config, TaskStatus.SUCCESSFUL, Action.DEPLOY);

    OperateServiceRequest request =
        OperateServiceRequest.newBuilder()
            .setServiceName(serviceName)
            .setEnvName(envName)
            .setConfig(configBuilder.build())
            .setIsComponentOperation(false)
            .setOperation("remove_component")
            .build();
    ResponseState finalState = new Operate(true, true);
    ServiceResponseAutomata responseAutomata = new ServiceResponseAutomata(finalState, finalState);

    // Act
    Flowable<OperateServiceResponse> flowableResponse =
        RxServiceServiceStub.operateService(request);

    flowableResponse
        .doOnNext(
            operateServiceResponse -> {
              log.debug(
                  String.format(
                      "Received message from deployer: %s", operateServiceResponse.toString()));
              responseAutomata.switchState(operateServiceResponse.getServiceResponse());
            })
        .subscribe(
            operateServiceResponse -> {
              if (responseAutomata.isComplete()) {
                // Check component undeploy task
                TestUtil.assertComponentTaskStatus(
                    connection, componentName1, Action.UNDEPLOY, TaskStatus.SUCCESSFUL);
                TestUtil.assertComponentTaskStatus(
                    connection, componentName2, Action.DEPLOY, TaskStatus.SUCCESSFUL);
                testContext.completeNow();
              }
            },
            testContext::failNow);
  }

  @Test
  void testOperateServiceRemoveComponentResumeAfterDisconnect(VertxTestContext testContext)
      throws InvalidProtocolBufferException {
    // Arrange
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    String envName = Constants.TEST_ENV_NAME;
    String componentName1 =
        String.format(
            "%s-%d", Constants.TEST_COMPONENT_NAME, ApplicationUtil.generateIntegerUUID());
    String componentName2 =
        String.format(
            "%s-%d", Constants.TEST_COMPONENT_NAME, ApplicationUtil.generateIntegerUUID());
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);

    Struct.Builder configBuilder = Struct.newBuilder();
    String configJson = "{\"component_name\":\"" + componentName1 + "\"}";
    JsonObject component1Config =
        TestUtil.getComponentConfig(
            componentName1,
            Constants.TEST_CONFIG_KEY,
            Constants.TEST_PARAM_KEY,
            "container",
            Map.of());
    JsonObject component2Config =
        TestUtil.getComponentConfig(
            componentName2,
            Constants.TEST_CONFIG_KEY,
            Constants.TEST_PARAM_KEY,
            "container",
            Map.of());

    com.google.gson.JsonObject jsonObject = JsonParser.parseString(configJson).getAsJsonObject();
    JsonFormat.parser().merge(new Gson().toJson(jsonObject), configBuilder);

    // Create service task
    Integer taskId =
        TestUtil.createServiceTask(
            connection, serviceName, envName, TaskStatus.SUCCESSFUL, Action.DEPLOY);
    // Create component task
    TestUtil.createComponentTask(
        connection, taskId, componentName1, component1Config, TaskStatus.SUCCESSFUL, Action.DEPLOY);
    TestUtil.createComponentTask(
        connection, taskId, componentName2, component2Config, TaskStatus.SUCCESSFUL, Action.DEPLOY);

    OperateServiceRequest request =
        OperateServiceRequest.newBuilder()
            .setServiceName(serviceName)
            .setEnvName(envName)
            .setConfig(configBuilder.build())
            .setIsComponentOperation(false)
            .setOperation("remove_component")
            .build();
    ResponseState finalState = new Operate(true, true);
    ServiceResponseAutomata responseAutomata = new ServiceResponseAutomata(finalState, finalState);

    ServiceResponseAutomata responseAutomataNew =
        new ServiceResponseAutomata(finalState, finalState);
    Flowable<OperateServiceResponse> operateServiceResponseFlowableInProgress =
        RxServiceServiceStub.operateService(request)
            .doOnNext(
                operateServiceResponse -> {
                  log.debug(
                      String.format(
                          "Received message from deployer: %s", operateServiceResponse.toString()));
                  responseAutomataNew.switchState(operateServiceResponse.getServiceResponse());
                  TestUtil.updateServiceStatus(
                      connection, taskId + 1, TaskStatus.IN_PROGRESS, TaskStatus.SUCCESSFUL);
                });

    // Act
    Flowable<OperateServiceResponse> flowableResponse =
        RxServiceServiceStub.operateService(request);

    flowableResponse
        .takeWhile(
            deployServiceResponse ->
                !(TaskStatus.IN_PROGRESS
                        .getValue()
                        .equals(
                            deployServiceResponse
                                .getServiceResponse()
                                .getServiceStatus()
                                .getServiceStatus())
                    && Action.OPERATE
                        .getName()
                        .equals(
                            deployServiceResponse
                                .getServiceResponse()
                                .getServiceStatus()
                                .getServiceAction())))
        .doOnComplete(
            () -> {
              log.debug("Resuming after disconnect");
              TestUtil.updateServiceStatus(
                  connection, taskId + 1, TaskStatus.SUCCESSFUL, TaskStatus.IN_PROGRESS);
            })
        .concatWith(operateServiceResponseFlowableInProgress)
        .doOnNext(
            operateServiceResponse -> {
              log.debug(
                  String.format(
                      "Received message from deployer: %s", operateServiceResponse.toString()));
              responseAutomata.switchState(operateServiceResponse.getServiceResponse());
            })
        .subscribe(
            operateServiceResponse -> {
              if (responseAutomataNew.isComplete()) {
                // Check component undeploy task
                TestUtil.assertComponentTaskStatus(
                    connection, componentName1, Action.UNDEPLOY, TaskStatus.SUCCESSFUL);
                TestUtil.assertComponentTaskStatus(
                    connection, componentName2, Action.DEPLOY, TaskStatus.SUCCESSFUL);
                testContext.completeNow();
              }
            },
            testContext::failNow);
  }

  @Test
  void testOperateServiceAddComponentExisting(VertxTestContext testContext)
      throws InvalidProtocolBufferException {
    // Arrange
    String name = "odin-operate-add-component";
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);

    String componentName = "cj-frontend-v1";
    Struct.Builder configBuilder = Struct.newBuilder();
    String configJson =
        """
                        {
                           "component_definition":  [{
                              "type": "application",
                              "name": "%s",
                              "version": "1.0.0",
                              "config": {
                                "artifact": {
                                  "name": "cj-frontend-v1",
                                  "version": "1.1.1"
                                }
                              }
                            }],
                            "provisioning_config":[{
                              "component_name": "%s",
                              "deployment_type": "aws_ec2"
                            }]
                        }
                        """
            .formatted(componentName, componentName);

    com.google.gson.JsonObject jsonObject = JsonParser.parseString(configJson).getAsJsonObject();
    JsonFormat.parser().merge(new Gson().toJson(jsonObject), configBuilder);

    OperateServiceRequest request =
        OperateServiceRequest.newBuilder()
            .setServiceName(name)
            .setEnvName(Constants.TEST_ENV_NAME)
            .setIsComponentOperation(false)
            .setOperation(Constants.SERVICE_OPERATION_ADD_COMPONENT)
            .setComponentName("cj-frontend-v1")
            .setConfig(configBuilder.build())
            .build();

    // Act
    Flowable<OperateServiceResponse> flowableResponse =
        RxServiceServiceStub.operateService(request);

    // Assert
    flowableResponse.subscribe(
        operateServiceResponse -> testContext.failNow("Should have thrown exception"),
        err -> {
          assertThat(err).isInstanceOf(StatusRuntimeException.class);
          assertThat(err)
              .hasMessageContaining(
                  "INVALID_ARGUMENT: Component cj-frontend-v1 cannot be added due to state deploy with status SUCCESSFUL");
          testContext.completeNow();
        });
  }

  @Test
  void testOperateServiceAddComponentExistingUndeployed(VertxTestContext testContext)
      throws InvalidProtocolBufferException {
    // Arrange
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    String envName = Constants.TEST_ENV_NAME;
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
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);

    Struct.Builder configBuilder = Struct.newBuilder();
    String configJson =
        """
                        {
                           "component_definition":  [{
                              "type": "application",
                              "name": "%s",
                              "version": "1.0.0",
                              "config": {
                                "artifact": {
                                  "name": "cj-frontend-v1",
                                  "version": "1.1.1"
                                }
                              }
                            }],
                            "provisioning_config":[{
                              "component_name": "%s",
                              "deployment_type": "aws_ec2"
                            }]
                        }
                        """
            .formatted(componentName, componentName);
    JsonObject jsonObject = new JsonObject(configJson);
    JsonFormat.parser().merge(jsonObject.toString(), configBuilder);

    OperateServiceRequest request =
        OperateServiceRequest.newBuilder()
            .setServiceName(serviceName)
            .setEnvName(envName)
            .setIsComponentOperation(false)
            .setOperation(Constants.SERVICE_OPERATION_ADD_COMPONENT)
            .setConfig(configBuilder.build())
            .build();

    // Create service task
    Integer taskId =
        TestUtil.createServiceTask(
            connection, serviceName, envName, TaskStatus.SUCCESSFUL, Action.DEPLOY);
    // Create component task
    TestUtil.createComponentTask(
        connection, taskId, componentName, componentConfig, TaskStatus.SUCCESSFUL, Action.UNDEPLOY);

    ResponseState finalState = new Operate(true, true);
    ResponseState initialState = new Validate(false, true, finalState);
    ServiceResponseAutomata responseAutomata =
        new ServiceResponseAutomata(initialState, finalState);

    // Act
    Flowable<OperateServiceResponse> flowableResponse =
        RxServiceServiceStub.operateService(request);

    flowableResponse
        .doOnNext(
            operateServiceResponse -> {
              log.debug(
                  String.format(
                      "Received message from deployer: %s", operateServiceResponse.toString()));
              responseAutomata.switchState(operateServiceResponse.getServiceResponse());
            })
        .subscribe(
            operateServiceResponse -> {
              if (responseAutomata.isComplete()) {
                // Check component deploy task
                TestUtil.assertComponentTaskStatus(
                    connection, componentName, Action.DEPLOY, TaskStatus.SUCCESSFUL);
                testContext.completeNow();
              }
            },
            testContext::failNow);
  }

  @Test
  void testOperateServiceRemovePreviouslyRemovedComponent(VertxTestContext testContext)
      throws InvalidProtocolBufferException {
    // Arrange
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    String envName = Constants.TEST_ENV_NAME;
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
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);

    Struct.Builder configBuilder = Struct.newBuilder();
    String configJson = "{\"component_name\":\"%s\"}".formatted(componentName);

    com.google.gson.JsonObject jsonObject = JsonParser.parseString(configJson).getAsJsonObject();
    JsonFormat.parser().merge(new Gson().toJson(jsonObject), configBuilder);

    OperateServiceRequest request =
        OperateServiceRequest.newBuilder()
            .setServiceName(serviceName)
            .setEnvName(envName)
            .setConfig(configBuilder.build())
            .setIsComponentOperation(false)
            .setOperation("remove_component")
            .build();
    int taskId =
        TestUtil.createServiceTask(
            connection, serviceName, envName, TaskStatus.SUCCESSFUL, Action.UNDEPLOY);
    TestUtil.createComponentTask(
        connection, taskId, componentName, componentConfig, TaskStatus.SUCCESSFUL, Action.UNDEPLOY);

    // Act
    Flowable<OperateServiceResponse> flowableResponse =
        RxServiceServiceStub.operateService(request);

    // Assert
    flowableResponse.subscribe(
        operateServiceResponse -> testContext.failNow("Should have thrown exception"),
        err -> {
          assertThat(err).isInstanceOf(StatusRuntimeException.class);
          assertThat(err)
              .hasMessageContaining(
                  "Component %s cannot be operated in state undeploy with status SUCCESSFUL"
                      .formatted(componentName));
          assertThat(err)
              .hasMessageContaining(
                  "Service %s cannot be operated in state undeploy with status SUCCESSFUL"
                      .formatted(serviceName));
          testContext.completeNow();
        });
  }

  @Test
  void testOperateServiceRemoveComponentWithNoComponentPresent(VertxTestContext testContext)
      throws InvalidProtocolBufferException {
    // Arrange
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    String envName = Constants.TEST_ENV_NAME;
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);

    Struct.Builder configBuilder = Struct.newBuilder();
    String configJson = "{\"component_name\":\"random-component\"}";

    com.google.gson.JsonObject jsonObject = JsonParser.parseString(configJson).getAsJsonObject();
    JsonFormat.parser().merge(new Gson().toJson(jsonObject), configBuilder);

    OperateServiceRequest request =
        OperateServiceRequest.newBuilder()
            .setServiceName(serviceName)
            .setEnvName(envName)
            .setConfig(configBuilder.build())
            .setIsComponentOperation(false)
            .setOperation("remove_component")
            .build();
    // Create service task
    TestUtil.createServiceTask(
        connection, serviceName, envName, TaskStatus.SUCCESSFUL, Action.DEPLOY);

    // Act
    Flowable<OperateServiceResponse> flowableResponse =
        RxServiceServiceStub.operateService(request);

    // Assert
    flowableResponse.subscribe(
        operateServiceResponse -> testContext.failNow("Should have thrown exception"),
        err -> {
          assertThat(err).isInstanceOf(StatusRuntimeException.class);
          assertThat(err)
              .hasMessageContaining(
                  "Component with name:random-component does not exist in service:%s"
                      .formatted(serviceName));
          testContext.completeNow();
        });
  }

  @Test
  void testOperateServiceComponent(VertxTestContext testContext)
      throws InvalidProtocolBufferException {
    // Arrange
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    String envName = Constants.TEST_ENV_NAME;
    String componentName = "odindemo5";
    JsonObject componentConfig =
        TestUtil.getComponentConfig(
            componentName,
            Constants.TEST_CONFIG_KEY,
            Constants.TEST_PARAM_KEY,
            "aws_ec2",
            Map.of());
    componentConfig.put(
        "operationConfig",
        new JsonObject().put("artifact_version", "1.0.31").put("num_instances", 10));
    // Create service task
    int taskId =
        TestUtil.createServiceTask(
            connection, serviceName, envName, TaskStatus.SUCCESSFUL, Action.DEPLOY);
    // Create component task
    TestUtil.createComponentTask(
        connection, taskId, componentName, componentConfig, TaskStatus.SUCCESSFUL, Action.DEPLOY);
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);

    String configJson =
        "{\"artifact_version\": \"1.0.31\",\"num_instances\":10 ,\"extraEnvVars\":{\"LOG_ENABLED\":\"true\"}}";
    Struct.Builder configBuilder = Struct.newBuilder();
    com.google.gson.JsonObject jsonObject = JsonParser.parseString(configJson).getAsJsonObject();
    JsonFormat.parser().merge(new Gson().toJson(jsonObject), configBuilder);

    OperateServiceRequest request =
        OperateServiceRequest.newBuilder()
            .setServiceName(serviceName)
            .setEnvName(envName)
            .setIsComponentOperation(true)
            .setOperation("redeploy")
            .setComponentName(componentName)
            .setConfig(configBuilder.build())
            .build();
    ResponseState finalState = new Operate(true, true);
    ResponseState initialState = new Validate(false, true, finalState);
    ServiceResponseAutomata responseAutomata =
        new ServiceResponseAutomata(initialState, finalState);

    // Act
    Flowable<OperateServiceResponse> flowableResponse =
        RxServiceServiceStub.operateService(request);

    // Assert
    flowableResponse
        .doOnNext(
            operateServiceResponse -> {
              log.debug(
                  String.format(
                      "Received message from deployer: %s", operateServiceResponse.toString()));
              responseAutomata.switchState(operateServiceResponse.getServiceResponse());
            })
        .subscribe(
            operateServiceResponse -> {
              if (responseAutomata.isComplete()) {
                // Check component operate task
                TestUtil.assertComponentTaskStatus(
                    connection, componentName, Action.OPERATE, TaskStatus.SUCCESSFUL);
                testContext.completeNow();
              }
            },
            testContext::failNow);
  }

  @Test
  void testOperateServiceComponentResumeAfterDisconnect(VertxTestContext testContext)
      throws InvalidProtocolBufferException {
    // Arrange
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    String envName = Constants.TEST_ENV_NAME;
    String componentName = "odindemo5";
    JsonObject componentConfig =
        TestUtil.getComponentConfig(
            componentName,
            Constants.TEST_CONFIG_KEY,
            Constants.TEST_PARAM_KEY,
            "aws_ec2",
            Map.of());
    componentConfig.put(
        "operationConfig",
        new JsonObject().put("artifact_version", "1.0.31").put("num_instances", 10));
    // Create service task
    int taskId =
        TestUtil.createServiceTask(
            connection, serviceName, envName, TaskStatus.SUCCESSFUL, Action.DEPLOY);
    // Create component task
    TestUtil.createComponentTask(
        connection, taskId, componentName, componentConfig, TaskStatus.SUCCESSFUL, Action.DEPLOY);
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);

    String configJson =
        "{\"artifact_version\": \"1.0.31\",\"num_instances\":10 ,\"extraEnvVars\":{\"LOG_ENABLED\":\"true\"}}";

    Struct.Builder configBuilder = Struct.newBuilder();
    com.google.gson.JsonObject jsonObject = JsonParser.parseString(configJson).getAsJsonObject();
    JsonFormat.parser().merge(new Gson().toJson(jsonObject), configBuilder);

    OperateServiceRequest request =
        OperateServiceRequest.newBuilder()
            .setServiceName(serviceName)
            .setEnvName(envName)
            .setIsComponentOperation(true)
            .setOperation("redeploy")
            .setComponentName(componentName)
            .setConfig(configBuilder.build())
            .build();
    ResponseState finalState = new Operate(true, true);
    ResponseState initialState = new Validate(false, true, finalState);
    ServiceResponseAutomata responseAutomata =
        new ServiceResponseAutomata(initialState, finalState);

    ServiceResponseAutomata responseAutomataNew =
        new ServiceResponseAutomata(finalState, finalState);

    // Act
    Flowable<OperateServiceResponse> flowableResponse =
        RxServiceServiceStub.operateService(request);

    Flowable<OperateServiceResponse> operateServiceResponseFlowableInProgress =
        RxServiceServiceStub.operateService(request)
            .doOnNext(
                operateServiceResponse -> {
                  log.debug(
                      String.format(
                          "Received message from deployer: %s", operateServiceResponse.toString()));
                  responseAutomataNew.switchState(operateServiceResponse.getServiceResponse());
                  TestUtil.updateServiceStatus(
                      connection, taskId + 1, TaskStatus.IN_PROGRESS, TaskStatus.SUCCESSFUL);
                });

    // Assert
    flowableResponse
        .takeWhile(
            serviceResponse ->
                !(TaskStatus.IN_PROGRESS
                        .getValue()
                        .equals(
                            serviceResponse
                                .getServiceResponse()
                                .getServiceStatus()
                                .getServiceStatus())
                    && Action.OPERATE
                        .getName()
                        .equals(
                            serviceResponse
                                .getServiceResponse()
                                .getServiceStatus()
                                .getServiceAction())))
        .doOnComplete(
            () -> {
              log.debug("Resuming after disconnect");
              TestUtil.updateServiceStatus(
                  connection, taskId + 1, TaskStatus.SUCCESSFUL, TaskStatus.IN_PROGRESS);
            })
        .concatWith(operateServiceResponseFlowableInProgress)
        .doOnNext(
            operateServiceResponse -> {
              log.debug(
                  String.format(
                      "Received message from deployer: %s", operateServiceResponse.toString()));
              responseAutomata.switchState(operateServiceResponse.getServiceResponse());
            })
        .subscribe(
            operateServiceResponse -> {
              if (responseAutomataNew.isComplete()) {
                // Check component operate task
                TestUtil.assertComponentTaskStatus(
                    connection, componentName, Action.OPERATE, TaskStatus.SUCCESSFUL);
                testContext.completeNow();
              }
            },
            testContext::failNow);
  }

  @Test
  void testOperateServiceComponentDiff(VertxTestContext testContext)
      throws InvalidProtocolBufferException {
    // Arrange
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    String envName = Constants.TEST_ENV_NAME;
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
            connection, serviceName, envName, TaskStatus.SUCCESSFUL, Action.DEPLOY);
    // Create component task
    TestUtil.createComponentTask(
        connection, taskId, componentName, componentConfig, TaskStatus.SUCCESSFUL, Action.DEPLOY);
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);

    String configJson =
        """
                        {
                            "artifact": {
                                "version": "1.2.0"
                            },
                            "baseImage": {
                                "filters": {
                                    "name": "amzn2-ami-hvm-2.0.20210326.0-x86_64-gp2",
                                    "owner": "amazon"
                                },
                                "instanceType": "t2.micro",
                                "sshUser": "ec2-user"
                            }
                        }
                        """;

    Struct.Builder configBuilder = Struct.newBuilder();
    com.google.gson.JsonObject jsonObject = JsonParser.parseString(configJson).getAsJsonObject();
    JsonFormat.parser().merge(new Gson().toJson(jsonObject), configBuilder);

    OperateComponentDiffRequest request =
        OperateComponentDiffRequest.newBuilder()
            .setServiceName(serviceName)
            .setEnvName(envName)
            .setOperationName("component_operation")
            .setComponentName(componentName)
            .setConfig(configBuilder.build())
            .build();

    Single<OperateComponentDiffResponse> flowableResponse =
        RxServiceServiceStub.operateComponentDiff(request);

    // Assert
    flowableResponse.subscribe(
        operateServiceResponse -> {
          Struct.Builder structBuilder = Struct.newBuilder();
          com.google.gson.JsonObject structJson =
              JsonParser.parseString(
                      """
                                                    {
                                                        "artifact": {
                                                            "version": "1.0.0"
                                                        },
                                                        "baseImage": {
                                                            "filters": {
                                                                "name": "<default>",
                                                                "owner": "<default>"
                                                            },
                                                            "instanceType": "<default>",
                                                            "sshUser": "<default>"
                                                        }
                                                    }
                                                        """)
                  .getAsJsonObject();
          JsonFormat.parser().merge(structJson.toString(), structBuilder);

          Struct oldConfig = structBuilder.build();

          Struct.Builder newStructBuilder = Struct.newBuilder();
          com.google.gson.JsonObject newStructJson =
              JsonParser.parseString(
                      """
                                                    {
                                                        "artifact": {
                                                            "version": "1.2.0"
                                                        },
                                                        "baseImage": {
                                                            "filters": {
                                                                "name": "amzn2-ami-hvm-2.0.20210326.0-x86_64-gp2",
                                                                "owner": "amazon"
                                                            },
                                                            "instanceType": "t2.micro",
                                                            "sshUser": "ec2-user"
                                                        }
                                                    }
                                                        """)
                  .getAsJsonObject();
          JsonFormat.parser().merge(newStructJson.toString(), newStructBuilder);

          Struct newConfig = newStructBuilder.build();

          Struct expectedOldDiff = operateServiceResponse.getOldValues();

          Struct expectedNewDiff = operateServiceResponse.getNewValues();

          assertThat(expectedOldDiff).isEqualTo(oldConfig);
          assertThat(expectedNewDiff).isEqualTo(newConfig);

          testContext.completeNow();
        },
        testContext::failNow);
  }

  @Test
  void testOperateServiceWhenPreviousDeploymentFailed(VertxTestContext testContext)
      throws InvalidProtocolBufferException {
    // Arrange
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    String envName = Constants.TEST_ENV_NAME;
    String componentName =
        String.format(
            "%s-%d", Constants.TEST_COMPONENT_NAME, ApplicationUtil.generateIntegerUUID());
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);

    String configJson =
        """
                        {
                           "component_definition":  [{
                              "type": "application",
                              "name": "%s",
                              "version": "1.0.0",
                              "config": {
                                "artifact": {
                                  "name": "cj-frontend-v1",
                                  "version": "1.1.1"
                                }
                              }
                            }],
                            "provisioning_config":[{
                              "component_name": "%s",
                              "deployment_type": "aws_ec2"
                            }]
                        }
                        """
            .formatted(componentName, componentName);
    Struct.Builder configBuilder = Struct.newBuilder();
    com.google.gson.JsonObject jsonObject = JsonParser.parseString(configJson).getAsJsonObject();
    JsonFormat.parser().merge(new Gson().toJson(jsonObject), configBuilder);

    // Create service task
    int id =
        TestUtil.createServiceTask(
            connection, serviceName, envName, TaskStatus.FAILED, Action.DEPLOY);

    TestUtil.createComponentTask(
        connection, id, componentName, new JsonObject(), TaskStatus.FAILED, Action.DEPLOY);

    OperateServiceRequest request =
        OperateServiceRequest.newBuilder()
            .setServiceName(serviceName)
            .setEnvName(envName)
            .setIsComponentOperation(false)
            .setOperation(Constants.SERVICE_OPERATION_ADD_COMPONENT)
            .setConfig(configBuilder.build())
            .build();

    // Act
    Flowable<OperateServiceResponse> flowableResponse =
        RxServiceServiceStub.operateService(request);

    // Assert
    flowableResponse.subscribe(
        operateServiceResponse -> testContext.failNow("Should have thrown exception"),
        err -> {
          assertThat(err).isInstanceOf(StatusRuntimeException.class);
          assertThat(err)
              .hasMessageContaining(
                  "Service "
                      + serviceName
                      + " cannot be operated in state deploy with status FAILED");
          testContext.completeNow();
        });
  }

  @Test
  void testOperateServiceWhenPreviousUndeploySuccessful(VertxTestContext testContext)
      throws InvalidProtocolBufferException {
    // Arrange
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    String envName = Constants.TEST_ENV_NAME;
    String componentName =
        String.format(
            "%s-%d", Constants.TEST_COMPONENT_NAME, ApplicationUtil.generateIntegerUUID());
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);

    String configJson =
        """
                        {
                           "component_definition":  [{
                              "type": "application",
                              "name": "%s",
                              "version": "1.0.0",
                              "config": {
                                "artifact": {
                                  "name": "cj-frontend-v1",
                                  "version": "1.1.1"
                                }
                              }
                            }],
                            "provisioning_config":[{
                              "component_name": "%s",
                              "deployment_type": "aws_ec2"
                            }]
                        }
                        """
            .formatted(componentName, componentName);
    Struct.Builder configBuilder = Struct.newBuilder();
    com.google.gson.JsonObject jsonObject = JsonParser.parseString(configJson).getAsJsonObject();
    JsonFormat.parser().merge(new Gson().toJson(jsonObject), configBuilder);

    // Create service task
    int id =
        TestUtil.createServiceTask(
            connection, serviceName, envName, TaskStatus.SUCCESSFUL, Action.UNDEPLOY);
    TestUtil.createComponentTask(
        connection, id, componentName, new JsonObject(), TaskStatus.SUCCESSFUL, Action.UNDEPLOY);

    OperateServiceRequest request =
        OperateServiceRequest.newBuilder()
            .setServiceName(serviceName)
            .setEnvName(envName)
            .setIsComponentOperation(false)
            .setOperation(Constants.SERVICE_OPERATION_ADD_COMPONENT)
            .setConfig(configBuilder.build())
            .build();

    // Act
    Flowable<OperateServiceResponse> flowableResponse =
        RxServiceServiceStub.operateService(request);

    // Assert
    flowableResponse.subscribe(
        operateServiceResponse -> testContext.failNow("Should have thrown exception"),
        err -> {
          assertThat(err).isInstanceOf(StatusRuntimeException.class);
          assertThat(err)
              .hasMessageContaining(
                  "Service "
                      + serviceName
                      + " cannot be operated in state undeploy with status SUCCESSFUL");
          testContext.completeNow();
        });
  }

  @Test
  void testUndeployService(VertxTestContext testContext) {
    // Arrange
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    String envName = Constants.TEST_ENV_NAME;
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
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);

    UndeployServiceRequest request =
        UndeployServiceRequest.newBuilder().setServiceName(serviceName).setEnvName(envName).build();

    // Create service task
    int taskId =
        TestUtil.createServiceTask(
            connection, serviceName, envName, TaskStatus.SUCCESSFUL, Action.DEPLOY);
    // Create component task
    TestUtil.createComponentTask(
        connection, taskId, componentName, componentConfig, TaskStatus.SUCCESSFUL, Action.DEPLOY);

    ResponseState finalState = new UnDeploy(true, true);
    ServiceResponseAutomata responseAutomata = new ServiceResponseAutomata(finalState, finalState);

    // Act
    Flowable<UndeployServiceResponse> flowableResponse =
        RxServiceServiceStub.undeployService(request);

    // Assert
    flowableResponse
        .doOnNext(
            undeployServiceResponse -> {
              log.debug(
                  String.format(
                      "Received message from deployer: %s", undeployServiceResponse.toString()));
              responseAutomata.switchState(undeployServiceResponse.getServiceResponse());
            })
        .subscribe(
            undeployServiceResponse -> {
              if (responseAutomata.isComplete()) {
                // Check service undeploy task
                TestUtil.assertServiceTaskStatus(
                    connection, serviceName, Action.UNDEPLOY, TaskStatus.SUCCESSFUL);
                testContext.completeNow();
              }
            },
            testContext::failNow);
  }

  @Test
  void testUndeployServiceWithUndeployInProgress(VertxTestContext testContext) {
    // Arrange
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    String envName = Constants.TEST_ENV_NAME;
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
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);

    UndeployServiceRequest request =
        UndeployServiceRequest.newBuilder().setServiceName(serviceName).setEnvName(envName).build();

    // Create service task
    int taskId =
        TestUtil.createServiceTask(
            connection, serviceName, envName, TaskStatus.IN_PROGRESS, Action.UNDEPLOY);
    // Create component task
    TestUtil.createComponentTask(
        connection,
        taskId,
        componentName,
        componentConfig,
        TaskStatus.IN_PROGRESS,
        Action.UNDEPLOY);

    ResponseState finalState = new UnDeploy(true, true);
    ServiceResponseAutomata responseAutomata = new ServiceResponseAutomata(finalState, finalState);

    // Act
    Flowable<UndeployServiceResponse> flowableResponse =
        RxServiceServiceStub.undeployService(request);

    // Assert
    flowableResponse
        .doOnNext(
            undeployServiceResponse -> {
              log.debug(
                  String.format(
                      "Received message from deployer: %s", undeployServiceResponse.toString()));
              responseAutomata.switchState(undeployServiceResponse.getServiceResponse());
              TestUtil.updateServiceStatus(
                  connection, taskId, TaskStatus.IN_PROGRESS, TaskStatus.SUCCESSFUL);
            })
        .subscribe(
            undeployServiceResponse -> {
              if (responseAutomata.isComplete()) {
                // Check service undeploy task
                TestUtil.assertServiceTaskStatus(
                    connection, serviceName, Action.UNDEPLOY, TaskStatus.SUCCESSFUL);
                testContext.completeNow();
              }
            },
            testContext::failNow);
  }

  @Test
  void testUndeployServiceAfterStatusEnv(VertxTestContext testContext) {
    // Arrange
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());
    String envName = Constants.TEST_ENV_NAME;
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
    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);

    UndeployServiceRequest request =
        UndeployServiceRequest.newBuilder().setServiceName(serviceName).setEnvName(envName).build();

    // Create service task
    int taskId =
        TestUtil.createServiceTask(
            connection, serviceName, envName, TaskStatus.SUCCESSFUL, Action.DEPLOY);
    // Create component task
    TestUtil.createComponentTask(
        connection, taskId, componentName, componentConfig, TaskStatus.SUCCESSFUL, Action.DEPLOY);

    // Create service task
    taskId =
        TestUtil.createServiceTask(
            connection, serviceName, envName, TaskStatus.SUCCESSFUL, Action.HEALTHCHECK, 2);
    // Create component task
    TestUtil.createComponentTask(
        connection,
        taskId,
        componentName,
        componentConfig,
        TaskStatus.SUCCESSFUL,
        Action.HEALTHCHECK);

    ResponseState finalState = new UnDeploy(true, true);
    ServiceResponseAutomata responseAutomata = new ServiceResponseAutomata(finalState, finalState);

    // Act
    Flowable<UndeployServiceResponse> flowableResponse =
        RxServiceServiceStub.undeployService(request);

    // Assert
    flowableResponse
        .doOnNext(
            undeployServiceResponse -> {
              log.debug(
                  String.format(
                      "Received message from deployer: %s", undeployServiceResponse.toString()));
              responseAutomata.switchState(undeployServiceResponse.getServiceResponse());
            })
        .subscribe(
            undeployServiceResponse -> {
              if (responseAutomata.isComplete()) {
                // Check service undeploy task
                TestUtil.assertServiceTaskStatus(
                    connection, serviceName, Action.UNDEPLOY, TaskStatus.SUCCESSFUL);
                testContext.completeNow();
              }
            },
            testContext::failNow);
  }

  /***
   *  To test component validation failed
   */
  @Test
  void testServiceDeployComponentValidationFailed(VertxTestContext testContext) {
    // Arrange
    String environmentName = Constants.TEST_ENV_NAME;
    String serviceName =
        String.format("%s-%d", Constants.TEST_SERVICE_NAME, ApplicationUtil.generateIntegerUUID());

    RxServiceServiceGrpc.RxServiceServiceStub RxServiceServiceStub =
        RxServiceServiceGrpc.newRxStub(channel);
    String componentName = Constants.TEST_COMPONENT_NAME;
    DeployServiceRequest request =
        TestUtil.getDeployServiceRequest(
            serviceName,
            environmentName,
            Constants.TEST_CONFIG_KEY,
            Constants.TEST_PARAM_KEY,
            Constants.TEST_DEPLOYMENT_TYPE,
            componentName);
    FailedComponent.addFailedServiceComponent(
        Pair.of(serviceName, Action.VALIDATE), List.of(Pair.of(componentName, Action.VALIDATE)));
    FailedComponent.addFailedServiceComponent(
        Pair.of(serviceName, Action.DEPLOY), List.of(Pair.of(componentName, Action.VALIDATE)));

    ResponseState finalState = new Validate(true, true, null);
    ResponseState initialState = new Validate(true, false, finalState);
    ServiceResponseAutomata responseAutomata =
        new ServiceResponseAutomata(initialState, finalState);

    // Act
    Flowable<DeployServiceResponse> flowableResponse = RxServiceServiceStub.deployService(request);

    // Assert
    flowableResponse
        .doOnNext(
            deployServiceResponse -> {
              log.debug(
                  String.format(
                      "Received message from deployer: %s", deployServiceResponse.toString()));
              responseAutomata.switchState(deployServiceResponse.getServiceResponse());
            })
        .subscribe(
            deployServiceResponse -> {
              if (responseAutomata.isComplete()) {
                TestUtil.assertComponentFailedMessage(deployServiceResponse, componentName);
                testContext.completeNow();
              }
            },
            testContext::failNow);
  }
}
