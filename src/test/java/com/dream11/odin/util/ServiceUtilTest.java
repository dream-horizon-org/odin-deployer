package com.dream11.odin.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dream11.odin.MainModule;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dto.ComponentData;
import com.dream11.odin.dto.ServiceData;
import com.dream11.odin.dto.UserDetails;
import com.dream11.odin.dto.constants.RequestMessageType;
import com.dream11.odin.dto.requestqueue.ComponentAction;
import com.dream11.odin.dto.requestqueue.ServiceRequestQueueMessage;
import com.dream11.odin.dto.requestqueue.Stage;
import com.dream11.odin.dto.v1.AccountInformation;
import com.dream11.odin.dto.v1.ComponentDefinition;
import com.dream11.odin.dto.v1.ComponentProvisioningConfig;
import com.dream11.odin.dto.v1.Environment;
import com.dream11.odin.dto.v1.ServiceDefinition;
import com.dream11.odin.entity.ServiceTaskEntity;
import com.dream11.odin.injector.GuiceInjector;
import com.google.inject.Guice;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@Slf4j
@ExtendWith({VertxExtension.class, MockitoExtension.class})
class ServiceUtilTest {

  @BeforeAll
  public static void setup(Vertx vertx) {
    GuiceInjector injector =
        new GuiceInjector(Guice.createInjector(List.of(new MainModule(vertx))));
    SharedDataUtil.setInstance(vertx, injector);
  }

  @Test
  void testCalculateServiceProvisioningHash() {
    // Create test data
    ServiceDefinition serviceDefinition = ServiceDefinition.newBuilder().build();
    List<ComponentProvisioningConfig> componentProvisioningConfigs = new ArrayList<>();

    // Populate componentProvisioningConfigs with test data
    ServiceData serviceData =
        ServiceData.builder()
            .serviceDefinition(serviceDefinition)
            .componentProvisioningConfigs(componentProvisioningConfigs)
            .build();

    // Call the method
    String hash = ServiceUtil.calculateServiceProvisioningHash(serviceData);

    // Expected hash
    StringBuilder expectedStringBuilder = new StringBuilder();
    String expectedHash =
        DigestUtils.sha256Hex(
            expectedStringBuilder
                .append(serviceData.getServiceDefinition().toString())
                .append(serviceData.getComponentProvisioningConfigs().toString())
                .toString());

    // Assertion
    assertEquals(expectedHash, hash);
  }

  @Test
  void testCreateServiceProvisioningConfigJson(Vertx vertx) {
    vertx.runOnContext(
        __ -> {
          // Create test data
          ServiceDefinition serviceDefinition = ServiceDefinition.newBuilder().build();
          List<ComponentProvisioningConfig> componentProvisioningConfigs = new ArrayList<>();

          ServiceData serviceData =
              ServiceData.builder()
                  .serviceDefinition(serviceDefinition)
                  .componentProvisioningConfigs(componentProvisioningConfigs)
                  .build();

          // Call the method
          JsonObject jsonObject = ServiceUtil.createServiceProvisioningConfigJson(serviceData);

          // Assertions
          assertThat(jsonObject).isNotNull();

          // Verify keys
          assertTrue(jsonObject.containsKey(Constants.DEFINITION_CONFIG_KEY));
          assertTrue(jsonObject.containsKey(Constants.PROVISIONING_CONFIG_KEY));
        });
  }

  @Test
  void testCreatePayload() {
    ServiceDefinition serviceDefinition = ServiceDefinition.newBuilder().build();
    Environment environment = Environment.newBuilder().build();
    List<ComponentAction> componentActions = new ArrayList<>();
    // Populate componentActions with test data
    Long taskId = 123L;

    // Call the method
    ServiceRequestQueueMessage payload =
        ServiceUtil.createPayload(
            serviceDefinition.getName(), environment.getName(), componentActions, taskId, 1L);

    // Assertions
    assertThat(payload).isNotNull();
    assertEquals(taskId, payload.getId());
    assertEquals(RequestMessageType.SERVICE, payload.getType());
    assertThat(payload.getBody()).isNotNull();
    assertEquals(serviceDefinition.getName(), payload.getBody().getServiceName());
    assertEquals(environment.getName(), payload.getBody().getEnvironmentName());
    assertEquals(componentActions, payload.getBody().getComponentActions());
  }

  @Test
  void testCreatePayloadStrictEnvNoUndeploy(Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(
        __ -> {
          String envName = "anyStrictEnv";

          ServiceDefinition serviceDefinition =
              ServiceDefinition.newBuilder().setName("test").build();
          Environment environment = Environment.newBuilder().setName("nonStrictEnv").build();

          ComponentDefinition c1 = TestUtil.getComponentDefinition("1");
          ComponentDefinition c2 = TestUtil.getComponentDefinition("2");

          ComponentProvisioningConfig p1 = TestUtil.getComponentProvisioningConfig("1");
          ComponentProvisioningConfig p2 = TestUtil.getComponentProvisioningConfig("2");

          ComponentAction ca1 =
              ComponentUtil.getComponentAction(
                  c1,
                  p1,
                  AccountInformation.newBuilder().build(),
                  Stage.builder().name(Action.DEPLOY).build());
          ComponentAction ca2 =
              ComponentUtil.getComponentAction(
                  c2,
                  p2,
                  AccountInformation.newBuilder().build(),
                  Stage.builder().name(Action.OPERATE).build());
          // Populate componentActions with test data
          Long taskId = 123L;

          try (MockedStatic<EnvironmentUtil> utilities =
              Mockito.mockStatic(EnvironmentUtil.class)) {
            // Assertions
            ServiceRequestQueueMessage payload =
                ServiceUtil.createPayload(
                    serviceDefinition.getName(),
                    environment.getName(),
                    List.of(ca1, ca2),
                    taskId,
                    1L);

            assertNotNull(payload);
            assertThat(payload.getBody().getComponentActions()).hasSize(2);

            testContext.completeNow();
          } catch (Error e) {
            testContext.failNow(e);
          }
        });
  }

  @Test
  void testCreateServiceTaskEntity(Vertx vertx, VertxTestContext vertxTestContext) {
    vertx.runOnContext(
        __ -> {
          ServiceDefinition serviceDefinition = ServiceDefinition.newBuilder().build();
          List<ComponentProvisioningConfig> componentProvisioningConfigs = new ArrayList<>();

          ServiceData serviceData =
              ServiceData.builder()
                  .serviceDefinition(serviceDefinition)
                  .componentProvisioningConfigs(componentProvisioningConfigs)
                  .build();

          Environment environment = Environment.newBuilder().build();
          Action action = Action.DEPLOY;
          UserDetails userDetails = UserDetails.builder().build();
          List<AccountInformation> environmentProviderAccounts = new ArrayList<>();
          AccountInformation accountInformation =
              AccountInformation.newBuilder().setProviderAccountName("testaccount").build();
          environmentProviderAccounts.add(accountInformation);
          // Call the method
          ServiceTaskEntity serviceTaskEntity =
              ServiceUtil.createServiceTaskEntity(serviceData, environment, action, userDetails, 0);

          // Assertions
          try {
            assertThat(serviceTaskEntity).isNotNull();
            assertEquals(action, serviceTaskEntity.getActions());
            assertEquals(TaskStatus.IN_PROGRESS, serviceTaskEntity.getStatus());
            assertEquals(Integer.valueOf(1), serviceTaskEntity.getVersion());
            vertxTestContext.completeNow();
          } catch (Error e) {
            vertxTestContext.failNow(e);
          }
        });
  }

  @Test
  void shouldReturnTrueWhenDeployIsInProgress() {
    // Create a ServiceTaskEntity with deploy action in progress
    ServiceTaskEntity serviceTaskEntity =
        ServiceTaskEntity.builder().actions(Action.DEPLOY).status(TaskStatus.IN_PROGRESS).build();

    // Call the method
    boolean result = ServiceUtil.isDeployInProgress(serviceTaskEntity);

    // Assertions
    assertTrue(result);
  }

  @Test
  void shouldReturnFalseWhenDeployIsNotInProgress() {
    // Create a ServiceTaskEntity without deploy action or in progress status
    ServiceTaskEntity serviceTaskEntity =
        ServiceTaskEntity.builder().actions(Action.VALIDATE).status(TaskStatus.SUCCESSFUL).build();

    // Call the method
    boolean result = ServiceUtil.isDeployInProgress(serviceTaskEntity);

    // Assertions
    assertFalse(result);
  }

  @Test
  void shouldConvertServiceDefinitionToJsonObjectSuccessfully() {
    // Create a ServiceDefinition object
    ServiceDefinition serviceDefinition =
        ServiceDefinition.newBuilder().setName("TestService").setVersion("1.0").build();

    // Call the method
    JsonObject jsonObject = ServiceUtil.serviceDefinitionToJsonObject(serviceDefinition);

    // Assertions
    assertThat(jsonObject).isNotNull();
    assertTrue(jsonObject.containsKey("name"));
    assertTrue(jsonObject.containsKey("version"));
    assertEquals("TestService", jsonObject.getString("name"));
    assertEquals("1.0", jsonObject.getString("version"));
  }

  @Test
  void shouldReturnFalseWhenOdinConfigIsNotModified() {
    // Create two ComponentData objects with identical Odin configuration
    ComponentDefinition componentDefinition =
        ComponentDefinition.newBuilder().setType("testType").setVersion("1").build();

    ComponentProvisioningConfig provisioningConfig =
        ComponentProvisioningConfig.newBuilder().setDeploymentType("testDeploymentType").build();
    ComponentData oldComponentData =
        ComponentData.builder()
            .componentDefinition(componentDefinition)
            .componentProvisioningConfig(provisioningConfig)
            .build();
    ComponentData newComponentData =
        ComponentData.builder()
            .componentDefinition(componentDefinition)
            .componentProvisioningConfig(provisioningConfig)
            .build();

    // Call the method
    boolean modified = ServiceUtil.isOdinConfigModified(oldComponentData, newComponentData);

    // Assertions
    assertFalse(modified);
  }

  @ParameterizedTest
  @MethodSource("odinConfigModifiedArguments")
  void shouldReturnTrueWhenOdinConfigIsModified(
      String componentType, String componentVersion, String deploymentType) {
    // Arrange
    ComponentData oldComponentData =
        ComponentData.builder()
            .componentDefinition(
                ComponentDefinition.newBuilder().setType("type1").setVersion("version1").build())
            .componentProvisioningConfig(
                ComponentProvisioningConfig.newBuilder()
                    .setDeploymentType("deploymentType1")
                    .build())
            .build();
    ComponentData newComponentData =
        ComponentData.builder()
            .componentDefinition(
                ComponentDefinition.newBuilder()
                    .setType(componentType)
                    .setVersion(componentVersion)
                    .build())
            .componentProvisioningConfig(
                ComponentProvisioningConfig.newBuilder().setDeploymentType(deploymentType).build())
            .build();

    // Act
    boolean modified = ServiceUtil.isOdinConfigModified(oldComponentData, newComponentData);

    // Assert
    assertThat(modified).isTrue();
  }

  private static Stream<Arguments> odinConfigModifiedArguments() {
    return Stream.of(
        Arguments.of("type1", "version2", "deploymentType1"),
        Arguments.of("type2", "version1", "deploymentType1"),
        Arguments.of("type1", "version1", "deploymentType2"));
  }

  @Test
  void shouldReturnTrueWhenProvisioningParamIsModified() {
    // Create two ComponentData objects with different deployment types
    ComponentData oldComponentData =
        ComponentData.builder()
            .componentDefinition(
                ComponentDefinition.newBuilder().setType("type1").setVersion("version1").build())
            .componentProvisioningConfig(
                ComponentProvisioningConfig.newBuilder()
                    .setParams(
                        Struct.newBuilder()
                            .putFields(
                                "randomKey",
                                Value.newBuilder().setStringValue("randomValue1").build())
                            .build())
                    .setDeploymentType("deploymentType1")
                    .build())
            .build();
    ComponentData newComponentData =
        ComponentData.builder()
            .componentDefinition(
                ComponentDefinition.newBuilder().setType("type1").setVersion("version1").build())
            .componentProvisioningConfig(
                ComponentProvisioningConfig.newBuilder()
                    .setParams(
                        Struct.newBuilder()
                            .putFields(
                                "randomKey",
                                Value.newBuilder().setStringValue("randomValue2").build())
                            .build())
                    .setDeploymentType("deploymentType1")
                    .build())
            .build();

    // Call the method
    boolean modified = ServiceUtil.isOdinConfigModified(oldComponentData, newComponentData);

    // Assertions
    assertFalse(modified);
  }

  @Test
  void shouldReturnFalseWhenConfigIsNotModified() {
    // Create two ComponentData objects with identical configurations
    ComponentData oldComponentData =
        ComponentData.builder()
            .componentDefinition(
                ComponentDefinition.newBuilder()
                    .setType("type1")
                    .setVersion("version1")
                    .setConfig(
                        Struct.newBuilder()
                            .putFields(
                                "randomConfigKey",
                                Value.newBuilder().setStringValue("randomConfigValue").build())
                            .build())
                    .build())
            .componentProvisioningConfig(
                ComponentProvisioningConfig.newBuilder()
                    .setParams(
                        Struct.newBuilder()
                            .putFields(
                                "randomKey",
                                Value.newBuilder().setStringValue("randomValue1").build())
                            .build())
                    .setDeploymentType("deploymentType1")
                    .build())
            .build();
    ComponentData newComponentData =
        ComponentData.builder()
            .componentDefinition(
                ComponentDefinition.newBuilder()
                    .setType("type1")
                    .setVersion("version1")
                    .setConfig(
                        Struct.newBuilder()
                            .putFields(
                                "randomConfigKey",
                                Value.newBuilder().setStringValue("randomConfigValue").build())
                            .build())
                    .build())
            .componentProvisioningConfig(
                ComponentProvisioningConfig.newBuilder()
                    .setParams(
                        Struct.newBuilder()
                            .putFields(
                                "randomKey",
                                Value.newBuilder().setStringValue("randomValue1").build())
                            .build())
                    .setDeploymentType("deploymentType1")
                    .build())
            .build();

    // Call the method
    boolean modified = ServiceUtil.isConfigModified(oldComponentData, newComponentData);

    // Assertions
    assertFalse(modified);
  }

  @Test
  void shouldDetectModificationWhenOdinConfigIsModified() {
    ComponentData oldComponentData =
        ComponentData.builder()
            .componentDefinition(
                ComponentDefinition.newBuilder()
                    .setType("type1")
                    .setVersion("version1")
                    .setConfig(
                        Struct.newBuilder()
                            .putFields(
                                "randomConfigKey",
                                Value.newBuilder().setStringValue("randomConfigValue").build())
                            .build())
                    .build())
            .componentProvisioningConfig(
                ComponentProvisioningConfig.newBuilder()
                    .setParams(
                        Struct.newBuilder()
                            .putFields(
                                "randomKey",
                                Value.newBuilder().setStringValue("randomValue1").build())
                            .build())
                    .setDeploymentType("deploymentType1")
                    .build())
            .build();
    ComponentData newComponentData =
        ComponentData.builder()
            .componentDefinition(
                ComponentDefinition.newBuilder()
                    .setType("type2")
                    .setVersion("version1")
                    .setConfig(
                        Struct.newBuilder()
                            .putFields(
                                "randomConfigKey",
                                Value.newBuilder().setStringValue("randomConfigValue").build())
                            .build())
                    .build())
            .componentProvisioningConfig(
                ComponentProvisioningConfig.newBuilder()
                    .setParams(
                        Struct.newBuilder()
                            .putFields(
                                "randomKey",
                                Value.newBuilder().setStringValue("randomValue1").build())
                            .build())
                    .setDeploymentType("deploymentType1")
                    .build())
            .build();

    // Call the method
    boolean modified = ServiceUtil.isConfigModified(oldComponentData, newComponentData);

    // Assertions
    assertTrue(modified);
  }

  @Test
  void shouldDetectModificationWhenConfigIsModified() {
    ComponentData oldComponentData =
        ComponentData.builder()
            .componentDefinition(
                ComponentDefinition.newBuilder()
                    .setType("type1")
                    .setVersion("version1")
                    .setConfig(
                        Struct.newBuilder()
                            .putFields(
                                "randomConfigKey",
                                Value.newBuilder().setStringValue("randomConfigValue1").build())
                            .build())
                    .build())
            .componentProvisioningConfig(
                ComponentProvisioningConfig.newBuilder()
                    .setParams(
                        Struct.newBuilder()
                            .putFields(
                                "randomKey",
                                Value.newBuilder().setStringValue("randomValue1").build())
                            .build())
                    .setDeploymentType("deploymentType1")
                    .build())
            .build();
    ComponentData newComponentData =
        ComponentData.builder()
            .componentDefinition(
                ComponentDefinition.newBuilder()
                    .setType("type1")
                    .setVersion("version1")
                    .setConfig(
                        Struct.newBuilder()
                            .putFields(
                                "randomConfigKey",
                                Value.newBuilder().setStringValue("randomConfigValue2").build())
                            .build())
                    .build())
            .componentProvisioningConfig(
                ComponentProvisioningConfig.newBuilder()
                    .setParams(
                        Struct.newBuilder()
                            .putFields(
                                "randomKey",
                                Value.newBuilder().setStringValue("randomValue1").build())
                            .build())
                    .setDeploymentType("deploymentType1")
                    .build())
            .build();

    // Call the method
    boolean modified = ServiceUtil.isConfigModified(oldComponentData, newComponentData);

    // Assertions
    assertTrue(modified);
  }

  @Test
  void shouldDetectModificationWhenParamsAreModified() {
    ComponentData oldComponentData =
        ComponentData.builder()
            .componentDefinition(
                ComponentDefinition.newBuilder()
                    .setType("type1")
                    .setVersion("version1")
                    .setConfig(
                        Struct.newBuilder()
                            .putFields(
                                "randomConfigKey",
                                Value.newBuilder().setStringValue("randomConfigValue").build())
                            .build())
                    .build())
            .componentProvisioningConfig(
                ComponentProvisioningConfig.newBuilder()
                    .setParams(
                        Struct.newBuilder()
                            .putFields(
                                "randomKey",
                                Value.newBuilder().setStringValue("randomValue1").build())
                            .build())
                    .setDeploymentType("deploymentType1")
                    .build())
            .build();
    ComponentData newComponentData =
        ComponentData.builder()
            .componentDefinition(
                ComponentDefinition.newBuilder()
                    .setType("type1")
                    .setVersion("version1")
                    .setConfig(
                        Struct.newBuilder()
                            .putFields(
                                "randomConfigKey",
                                Value.newBuilder().setStringValue("randomConfigValue").build())
                            .build())
                    .build())
            .componentProvisioningConfig(
                ComponentProvisioningConfig.newBuilder()
                    .setParams(
                        Struct.newBuilder()
                            .putFields(
                                "randomKey",
                                Value.newBuilder().setStringValue("randomValue2").build())
                            .build())
                    .setDeploymentType("deploymentType1")
                    .build())
            .build();

    // Call the method
    boolean modified = ServiceUtil.isConfigModified(oldComponentData, newComponentData);

    // Assertions
    assertTrue(modified);
  }
}
