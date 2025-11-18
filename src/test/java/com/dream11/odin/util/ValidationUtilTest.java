package com.dream11.odin.util;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.dream11.grpc.error.GrpcException;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dao.EnvironmentDao;
import com.dream11.odin.dto.ComponentData;
import com.dream11.odin.dto.ComponentIdentifier;
import com.dream11.odin.dto.ServiceData;
import com.dream11.odin.dto.UserDetails;
import com.dream11.odin.dto.v1.AccountInformation;
import com.dream11.odin.dto.v1.ComponentDefinition;
import com.dream11.odin.dto.v1.ComponentProvisioningConfig;
import com.dream11.odin.dto.v1.ProviderAccount;
import com.dream11.odin.dto.v1.ProvisioningConfig;
import com.dream11.odin.dto.v1.ServiceDefinition;
import com.dream11.odin.entity.ComponentValidateTaskEntity;
import com.dream11.odin.entity.ServiceValidateTaskEntity;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountResponse;
import com.dream11.odin.grpc.service.ServiceStatus;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.reactivex.Completable;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class ValidationUtilTest {

  @Test
  void shouldReturnTrueWhenValidationIsSuccessful() {
    // Create a ServiceStatus object indicating successful status
    ServiceStatus status =
        ServiceStatus.newBuilder().setServiceStatus(TaskStatus.SUCCESSFUL.getValue()).build();

    // Call the method
    boolean successful = ValidationUtil.isValidateSuccessful(status);

    // Assertions
    assertTrue(successful);
  }

  @Test
  void shouldReturnFalseWhenValidationFails() {
    // Create a ServiceStatus object indicating a status other than successful
    ServiceStatus status =
        ServiceStatus.newBuilder().setServiceStatus(TaskStatus.FAILED.getValue()).build();

    // Call the method
    boolean successful = ValidationUtil.isValidateSuccessful(status);

    // Assertions
    assertFalse(successful);
  }

  @Test
  void testCreateComponentValidateTaskEntity(Vertx vertx) {
    vertx.runOnContext(
        __ -> {
          ComponentDefinition componentDefinition =
              ComponentDefinition.newBuilder()
                  .setName("testComponentName")
                  .setVersion("testComponentVersion")
                  .setConfig(
                      Struct.newBuilder()
                          .putFields(
                              "testComponentConfig",
                              Value.newBuilder().setStringValue("testComponentConfigValue").build())
                          .build())
                  .build();
          ComponentProvisioningConfig componentProvisioningConfig =
              ComponentProvisioningConfig.newBuilder()
                  .setDeploymentType("testDeploymentType")
                  .setParams(
                      Struct.newBuilder()
                          .putFields(
                              "testParams",
                              Value.newBuilder().setStringValue("testParamValue").build())
                          .build())
                  .build();
          ServiceValidateTaskEntity serviceValidateTaskEntity =
              ServiceValidateTaskEntity.builder().name("testService").build();
          UserDetails userDetails = UserDetails.builder().build();

          // Call the method
          ComponentValidateTaskEntity taskEntity =
              ValidationUtil.createComponentValidateTaskEntity(
                  ComponentData.builder()
                      .componentDefinition(componentDefinition)
                      .componentProvisioningConfig(componentProvisioningConfig)
                      .build(),
                  serviceValidateTaskEntity,
                  userDetails);

          // Assertions
          assertThat(taskEntity).isNotNull();
          assertEquals(serviceValidateTaskEntity, taskEntity.getServiceValidateTaskEntity());
          assertEquals("testComponentName", taskEntity.getComponentName());
          assertEquals(TaskStatus.IN_PROGRESS, taskEntity.getStatus());
          assertEquals(Integer.valueOf(1), taskEntity.getVersion());
        });
  }

  @Test
  void testCreateComponentValidateTaskEntities(Vertx vertx) {
    vertx.runOnContext(
        __ -> {
          ComponentDefinition component1 =
              ComponentDefinition.newBuilder()
                  .setName("testComponentName1")
                  .setVersion("testComponentVersion1")
                  .setConfig(
                      Struct.newBuilder()
                          .putFields(
                              "testComponentConfig1",
                              Value.newBuilder()
                                  .setStringValue("testComponentConfigValue1")
                                  .build())
                          .build())
                  .build();
          ComponentDefinition component2 =
              ComponentDefinition.newBuilder()
                  .setName("testComponentName2")
                  .setVersion("testComponentVersion2")
                  .setConfig(
                      Struct.newBuilder()
                          .putFields(
                              "testComponentConfig2",
                              Value.newBuilder()
                                  .setStringValue("testComponentConfigValue2")
                                  .build())
                          .build())
                  .build();

          ServiceDefinition serviceDefinition =
              ServiceDefinition.newBuilder()
                  .addComponents(component1)
                  .addComponents(component2)
                  .build();

          Map<ComponentIdentifier, ComponentData> componentDataMap = new HashMap<>();
          ComponentProvisioningConfig provisioningConfig1 =
              ComponentProvisioningConfig.newBuilder()
                  .setDeploymentType("testDeploymentType1")
                  .setParams(
                      Struct.newBuilder()
                          .putFields(
                              "testParams1",
                              Value.newBuilder().setStringValue("testParamValue1").build())
                          .build())
                  .build();
          ComponentData componentData1 =
              ComponentData.builder()
                  .componentDefinition(component1)
                  .componentProvisioningConfig(provisioningConfig1)
                  .build();

          componentDataMap.put(
              ComponentIdentifier.builder()
                  .componentName("testComponentName1")
                  .action(Action.VALIDATE)
                  .build(),
              componentData1);

          ComponentProvisioningConfig provisioningConfig2 =
              ComponentProvisioningConfig.newBuilder()
                  .setDeploymentType("testDeploymentType2")
                  .setParams(
                      Struct.newBuilder()
                          .putFields(
                              "testParams2",
                              Value.newBuilder().setStringValue("testParamValue2").build())
                          .build())
                  .build();
          ComponentData componentData2 =
              ComponentData.builder()
                  .componentDefinition(component2)
                  .componentProvisioningConfig(provisioningConfig2)
                  .build();
          componentDataMap.put(
              ComponentIdentifier.builder()
                  .componentName("testComponentName2")
                  .action(Action.VALIDATE)
                  .build(),
              componentData2);

          ServiceValidateTaskEntity serviceValidateTaskEntity =
              ServiceValidateTaskEntity.builder().name("testService").build();
          UserDetails userDetails = UserDetails.builder().build();

          // Call the method
          List<ComponentValidateTaskEntity> entities =
              ValidationUtil.createComponentValidateTaskEntities(
                  serviceDefinition, componentDataMap, serviceValidateTaskEntity, userDetails);

          // Assertions
          assertThat(entities).isNotNull();
          assertEquals(2, entities.size());

          // Verify component1 entity
          ComponentValidateTaskEntity entity1 = entities.get(0);
          assertEquals(serviceValidateTaskEntity, entity1.getServiceValidateTaskEntity());
          assertEquals("testComponentName1", entity1.getComponentName());
          assertEquals(TaskStatus.IN_PROGRESS, entity1.getStatus());
          assertEquals(Integer.valueOf(1), entity1.getVersion());

          // Verify component2 entity
          ComponentValidateTaskEntity entity2 = entities.get(1);
          assertEquals(serviceValidateTaskEntity, entity2.getServiceValidateTaskEntity());
          assertEquals("testComponentName2", entity2.getComponentName());
          assertEquals(TaskStatus.IN_PROGRESS, entity2.getStatus());
          assertEquals(Integer.valueOf(1), entity2.getVersion());
        });
  }

  @Test
  void testCreateServiceValidateTaskEntity(Vertx vertx) {
    vertx.runOnContext(
        __ -> {
          // Mock input data
          ServiceDefinition serviceDefinition =
              ServiceDefinition.newBuilder()
                  .setName("testService")
                  .setVersion("testVersion")
                  .build();
          ServiceData serviceData =
              ServiceData.builder()
                  .serviceDefinition(serviceDefinition)
                  .componentProvisioningConfigs(
                      List.of(
                          ComponentProvisioningConfig.newBuilder()
                              .setComponentName("testComponentName")
                              .build()))
                  .build();
          UserDetails userDetails = UserDetails.builder().build();

          // Call the method
          ServiceValidateTaskEntity entity =
              ValidationUtil.createServiceValidateTaskEntity(serviceData, userDetails);

          // Assertions
          assertThat(entity).isNotNull();
          assertEquals("testService", entity.getName());
          assertEquals("testVersion", entity.getServiceVersion());
          assertEquals(TaskStatus.IN_PROGRESS, entity.getStatus());
          assertEquals(Integer.valueOf(1), entity.getVersion());
        });
  }

  @Test
  void shouldPassValidationWhenDeploymentTypePrefixIsValid(
      Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(
        __ -> {
          // Arrange
          ProvisioningConfig provisioningConfig =
              ProvisioningConfig.newBuilder()
                  .addAllComponentProvisioningConfig(
                      List.of(
                          ComponentProvisioningConfig.newBuilder()
                              .setDeploymentType("aws_ec2")
                              .build(),
                          ComponentProvisioningConfig.newBuilder()
                              .setDeploymentType("aws_ec2")
                              .build()))
                  .build();
          List<AccountInformation> accountInformationList =
              List.of(
                  AccountInformation.newBuilder()
                      .setProviderAccountName("test")
                      .setServiceAccountsSnapshot(
                          GetProviderAccountResponse.newBuilder()
                              .setAccount(
                                  ProviderAccount.newBuilder().setName("test").setProvider("AWS")))
                      .build());

          // Act & Assert
          ValidationUtil.validateDeploymentTypePrefix(
                  provisioningConfig, accountInformationList, "")
              .subscribe(testContext::completeNow, testContext::failNow);
        });
  }

  @Test
  void shouldFailValidationWhenDeploymentTypePrefixIsInvalid(
      Vertx vertx, VertxTestContext testContext) {

    vertx.runOnContext(
        __ -> {
          // Arrange
          ProvisioningConfig provisioningConfig =
              ProvisioningConfig.newBuilder()
                  .addAllComponentProvisioningConfig(
                      List.of(
                          ComponentProvisioningConfig.newBuilder()
                              .setComponentName("test1")
                              .setDeploymentType("ec2")
                              .build(),
                          ComponentProvisioningConfig.newBuilder()
                              .setComponentName("test2")
                              .setDeploymentType("ec2")
                              .build()))
                  .build();
          List<AccountInformation> accountInformationList =
              List.of(
                  AccountInformation.newBuilder()
                      .setProviderAccountName("test")
                      .setServiceAccountsSnapshot(
                          GetProviderAccountResponse.newBuilder()
                              .setAccount(
                                  ProviderAccount.newBuilder().setName("test").setProvider("AWS")))
                      .build());

          // Act & Assert
          ValidationUtil.validateDeploymentTypePrefix(
                  provisioningConfig, accountInformationList, "")
              .subscribe(
                  () -> testContext.failNow("IllegalArgumentException should be thrown"),
                  err -> {
                    try {
                      assertThat(err).isInstanceOf(GrpcException.class);
                      assertEquals(
                          "Invalid deployment type {test2=Invalid deploymentType [ec2]. DeploymentType must start with [aws], "
                              + "test1=Invalid deploymentType [ec2]. DeploymentType must start with [aws]}",
                          err.getMessage());
                      testContext.completeNow();
                    } catch (Throwable e) {
                      testContext.failNow(e);
                    }
                  });
        });
  }

  @Test
  void shouldPassValidationWhenEnvironmentIsRunning(Vertx vertx) {
    vertx.runOnContext(
        __ -> {
          // Mock input data
          UserDetails userDetails = UserDetails.builder().userId("1").orgId(1L).build();
          EnvironmentDao environmentDao = mock(EnvironmentDao.class);
          String envName = "TestEnv";

          // Call the method
          Completable completable =
              ValidationUtil.validateEnvState(environmentDao, envName, userDetails);

          // Assertions
          completable.test().assertComplete();
        });
  }

  @Test
  void shouldPassValidationWhenServiceIsValid(Vertx vertx) {
    vertx.runOnContext(
        __ -> {
          // Mock input data
          ServiceData serviceData = ServiceData.builder().build();

          // Call the method
          Completable completable = ValidationUtil.validateService(serviceData);

          // Assertions
          completable.test().assertComplete();
        });
  }
}
