package com.dream11.odin.util;

import static com.dream11.odin.Constants.EMPTY_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dream11.odin.MainModule;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dto.ComponentData;
import com.dream11.odin.dto.ComponentId;
import com.dream11.odin.dto.ServiceData;
import com.dream11.odin.dto.UserDetails;
import com.dream11.odin.dto.requestqueue.ComponentAction;
import com.dream11.odin.dto.requestqueue.Stage;
import com.dream11.odin.dto.v1.AccountInformation;
import com.dream11.odin.dto.v1.ComponentDefinition;
import com.dream11.odin.dto.v1.ComponentProvisioningConfig;
import com.dream11.odin.dto.v1.ProviderAccount;
import com.dream11.odin.dto.v1.ServiceDefinition;
import com.dream11.odin.entity.ComponentTaskEntity;
import com.dream11.odin.entity.ServiceTaskEntity;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountResponse;
import com.dream11.odin.injector.GuiceInjector;
import com.google.inject.Guice;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class ComponentUtilTest {
  @BeforeAll
  public static void setup(Vertx vertx) {
    GuiceInjector injector =
        new GuiceInjector(Guice.createInjector(List.of(new MainModule(vertx))));
    SharedDataUtil.setInstance(vertx, injector);
  }

  @Test
  void testCreateComponentTaskEntities(Vertx vertx) {
    vertx.runOnContext(
        __ -> {
          // Arrange
          Map<ComponentId, ComponentData> componentDataMap = new HashMap<>();
          ServiceTaskEntity serviceTaskEntity = ServiceTaskEntity.builder().build();
          List<ComponentAction> componentActions = new ArrayList<>();
          UserDetails userDetails = UserDetails.builder().build();

          // Mock the ComponentData and ComponentId
          ComponentData componentData =
              ComponentData.builder()
                  .componentDefinition(
                      ComponentDefinition.newBuilder().setName("testComponentName").build())
                  .componentProvisioningConfig(ComponentProvisioningConfig.newBuilder().build())
                  .build();
          ComponentId componentId =
              ComponentId.builder()
                  .componentName("testComponentName")
                  .action(Action.DEPLOY)
                  .build();
          componentDataMap.put(componentId, componentData);

          // Mock the ComponentAction and Stage
          ComponentAction componentAction =
              ComponentAction.builder()
                  .componentName("testComponentName")
                  .stage(Stage.builder().name(Action.DEPLOY).build())
                  .build();
          componentActions.add(componentAction);

          // Act
          List<ComponentTaskEntity> result =
              ComponentUtil.createComponentTaskEntities(
                  componentDataMap, serviceTaskEntity, componentActions, userDetails);

          // Assert
          assertEquals(1, result.size());
          assertEquals("testComponentName", result.get(0).getComponentName());
        });
  }

  @Test
  void testCreateComponentTaskEntity(Vertx vertx) {
    vertx.runOnContext(
        __ -> {
          // Arrange
          ComponentDefinition component =
              ComponentDefinition.newBuilder().setName("testComponent").build();
          ComponentProvisioningConfig componentProvisioningConfig =
              ComponentProvisioningConfig.newBuilder().build();
          ComponentData componentData =
              ComponentData.builder()
                  .componentDefinition(component)
                  .componentProvisioningConfig(componentProvisioningConfig)
                  .build();
          ServiceTaskEntity serviceTaskEntity = ServiceTaskEntity.builder().build();
          Action actions = Action.DEPLOY;
          TaskStatus status = TaskStatus.SUCCESSFUL;
          UserDetails userDetails = UserDetails.builder().build();
          Map<String, Object> accounts = Map.of("key1", "value1");

          // Act
          ComponentTaskEntity result =
              ComponentUtil.createComponentTaskEntity(
                  componentData, serviceTaskEntity, actions, status, userDetails, accounts);

          // Assert
          assertEquals("testComponent", result.getComponentName());
          assertEquals(Integer.valueOf(1), result.getVersion());
          assertEquals(status, result.getStatus());
          assertEquals(actions, result.getAction());
          assertEquals(new JsonObject(accounts), result.getAccounts());
        });
  }

  @Test
  void testGetComponentActions(Vertx vertx) {

    vertx.runOnContext(
        __ -> {
          // Mock input data
          Map<ComponentId, ComponentData> componentDataMap = new HashMap<>();
          AccountInformation environmentProviderAccounts = AccountInformation.newBuilder().build();
          ComponentId componentId1 =
              ComponentId.builder().componentName("testComponent1").action(Action.DEPLOY).build();
          ComponentId componentId2 =
              ComponentId.builder().componentName("testComponent2").action(Action.VALIDATE).build();
          ComponentData componentData1 =
              ComponentData.builder()
                  .componentDefinition(
                      ComponentDefinition.newBuilder().setName("testComponent1").build())
                  .componentProvisioningConfig(ComponentProvisioningConfig.newBuilder().build())
                  .environmentProviderAccounts(environmentProviderAccounts)
                  .build();
          ComponentData componentData2 =
              ComponentData.builder()
                  .componentDefinition(
                      ComponentDefinition.newBuilder().setName("testComponent2").build())
                  .componentProvisioningConfig(ComponentProvisioningConfig.newBuilder().build())
                  .environmentProviderAccounts(environmentProviderAccounts)
                  .build();
          componentDataMap.put(componentId1, componentData1);
          componentDataMap.put(componentId2, componentData2);

          Map<String, Stage> componentsStageMap = new HashMap<>();

          // Call the method
          List<ComponentAction> componentActions =
              ComponentUtil.getComponentActions(componentDataMap, componentsStageMap);

          // Assertions
          assertThat(componentActions).isNotNull();
          assertEquals(2, componentActions.size());

          // Verify properties of each ComponentAction
          for (ComponentAction action : componentActions) {
            assertThat(action.getId()).isNotNull();
            assertThat(action.getComponentName()).isNotNull();
            assertThat(action.getComponentType()).isNotNull();
            assertThat(action.getComponentVersion()).isNotNull();
            assertThat(action.getBaseConfig()).isNotNull();
            assertThat(action.getFlavourConfig()).isNotNull();
            assertThat(action.getDeploymentType()).isNotNull();
            assertThat(action.getStage()).isNotNull();
            assertThat(action.getProvider()).isNotNull();
            if (!action.getStage().getName().equals(Action.VALIDATE)) {
              assertThat(action.getDependsOn()).isNotNull();
              assertFalse(action.getDependsOn().isEmpty());
            } else {
              assertThat(action.getDependsOn()).isNotNull();
              assertTrue(action.getDependsOn().isEmpty());
            }
          }
        });
  }

  @Test
  void testGetComponentAction(Vertx vertx) {
    vertx.runOnContext(
        __ -> {
          // Mock input data
          ComponentDefinition componentDefinition =
              ComponentDefinition.newBuilder().setName("testComponent").build();
          ComponentProvisioningConfig componentProvisioningConfig =
              ComponentProvisioningConfig.newBuilder().setComponentName("testComponent").build();
          AccountInformation accountInformation =
              AccountInformation.newBuilder()
                  .setServiceAccountsSnapshot(
                      GetProviderAccountResponse.newBuilder()
                          .setAccount(ProviderAccount.newBuilder().setProvider("aws_ec2").build())
                          .build())
                  .build();
          Stage stage = Stage.builder().name(Action.DEPLOY).build();

          // Call the method
          ComponentAction componentAction =
              ComponentUtil.getComponentAction(
                  componentDefinition, componentProvisioningConfig, accountInformation, stage);

          // Assertions
          assertThat(componentAction).isNotNull();
          assertEquals(Integer.valueOf(1), componentAction.getId());
          assertEquals(componentDefinition.getName(), componentAction.getComponentName());
          assertEquals(componentDefinition.getType(), componentAction.getComponentType());
          assertEquals(componentDefinition.getVersion(), componentAction.getComponentVersion());
          assertThat(componentAction.getBaseConfig()).isNotNull();
          assertThat(componentAction.getFlavourConfig()).isNotNull();
          assertEquals(
              componentProvisioningConfig.getDeploymentType(), componentAction.getDeploymentType());
          assertThat(componentAction.getProvider()).isNotNull();
          assertEquals(stage, componentAction.getStage());
          assertThat(componentAction.getDependsOn()).isNotNull();
          assertTrue(componentAction.getDependsOn().isEmpty());
        });
  }

  @Test
  void testGetComponentAction() {
    // Mock input data
    ComponentId componentId =
        ComponentId.builder().componentName("ComponentName").action(Action.DEPLOY).build();
    List<ComponentAction> componentActions = new ArrayList<>();
    componentActions.add(
        ComponentAction.builder()
            .componentName("ComponentName")
            .stage(Stage.builder().name(Action.DEPLOY).build())
            .build());

    // Call the method
    ComponentAction componentAction =
        ComponentUtil.getComponentAction(componentId, componentActions);

    // Assertions
    assertThat(componentAction).isNotNull();
    assertEquals("ComponentName", componentAction.getComponentName());
    assertEquals(Action.DEPLOY, componentAction.getStage().getName());
  }

  @Test
  void testGetComponentDependencies() {
    // Mock input data
    ComponentId componentId =
        ComponentId.builder().componentName("ComponentName").action(Action.DEPLOY).build();
    Map<ComponentId, ComponentData> componentDataMap = new HashMap<>();

    ComponentDefinition componentDefinition =
        ComponentDefinition.newBuilder()
            .addDependsOn("Component1")
            .addDependsOn("Component2")
            .build();
    ComponentProvisioningConfig componentProvisioningConfig =
        ComponentProvisioningConfig.newBuilder().setComponentName("ComponentName").build();
    ComponentData componentData =
        ComponentData.builder()
            .componentDefinition(componentDefinition)
            .componentProvisioningConfig(componentProvisioningConfig)
            .build();
    componentDataMap.put(
        ComponentId.builder().componentName("ComponentName").action(Action.DEPLOY).build(),
        componentData);

    List<ComponentAction> componentActions = new ArrayList<>();
    componentActions.add(
        ComponentAction.builder()
            .id(1)
            .componentName("Component1")
            .stage(Stage.builder().name(Action.VALIDATE).build())
            .build());
    componentActions.add(
        ComponentAction.builder()
            .id(2)
            .componentName("Component2")
            .stage(Stage.builder().name(Action.DEPLOY).build())
            .build());

    // Call the method
    List<Integer> componentDependencies =
        ComponentUtil.getComponentDependencies(componentId, componentDataMap, componentActions);

    // Assertions
    assertThat(componentDependencies).isNotNull();
    assertEquals(2, componentDependencies.size());
    assertTrue(componentDependencies.contains(1));
    assertTrue(componentDependencies.contains(2));
  }

  @Test
  void shouldReturnComponentActionIdWhenFound() {
    // Mock input data
    String componentName = "ComponentName";
    List<ComponentAction> componentActions = new ArrayList<>();
    componentActions.add(ComponentAction.builder().id(1).componentName("ComponentName").build());

    // Call the method
    Integer componentActionId = ComponentUtil.getComponentActionId(componentName, componentActions);

    // Assertions
    assertThat(componentActionId).isNotNull();
    assertEquals(Integer.valueOf(1), componentActionId);
  }

  @Test
  void shouldReturnNullWhenComponentActionIdNotFound() {
    // Mock input data
    String componentName = "NonExistentComponent";
    List<ComponentAction> componentActions = new ArrayList<>();
    componentActions.add(ComponentAction.builder().id(1).componentName("ComponentName").build());

    assertThrows(
        IllegalArgumentException.class,
        () -> ComponentUtil.getComponentActionId(componentName, componentActions));
  }

  @Test
  void testGenerateAllComponentStages() {
    // Mock input data
    List<String> componentNames = Arrays.asList("Component1", "Component2", "Component3");
    Action action = Action.DEPLOY;
    Map<String, Object> stageConfig = new HashMap<>();
    stageConfig.put("key1", "value1");
    stageConfig.put("key2", "value2");

    // Call the method
    Map<String, Stage> allComponentStages =
        ComponentUtil.generateAllComponentStages(componentNames, action, stageConfig);

    // Assertions
    assertThat(allComponentStages).isNotNull();
    assertEquals(3, allComponentStages.size());
    assertTrue(allComponentStages.containsKey("Component1"));
    assertTrue(allComponentStages.containsKey("Component2"));
    assertTrue(allComponentStages.containsKey("Component3"));
    assertEquals(action, allComponentStages.get("Component1").getName());
    assertEquals(action, allComponentStages.get("Component2").getName());
    assertEquals(action, allComponentStages.get("Component3").getName());
    assertEquals(stageConfig, allComponentStages.get("Component1").getConfig());
    assertEquals(stageConfig, allComponentStages.get("Component2").getConfig());
    assertEquals(stageConfig, allComponentStages.get("Component3").getConfig());
  }

  @Test
  void testComponentProvisioningConfigsToJsonArray() {
    // Mock input data
    List<ComponentProvisioningConfig> componentProvisioningConfigs = new ArrayList<>();
    componentProvisioningConfigs.add(
        ComponentProvisioningConfig.newBuilder().setComponentName("testComponent1").build());
    componentProvisioningConfigs.add(
        ComponentProvisioningConfig.newBuilder().setComponentName("testComponent2").build());

    // Call the method
    JsonArray jsonArray =
        ComponentUtil.componentProvisioningConfigsToJsonArray(componentProvisioningConfigs);

    // Assertions
    assertThat(jsonArray).isNotNull();
    assertEquals(2, jsonArray.size());
    assertEquals("testComponent1", jsonArray.getJsonObject(0).getString("component_name"));
    assertEquals("testComponent2", jsonArray.getJsonObject(1).getString("component_name"));
  }

  @Test
  void testComponentAndProvisioningConfigToJson(Vertx vertx) {
    vertx.runOnContext(
        __ -> {
          // Mock input data
          ComponentDefinition componentDefinition = ComponentDefinition.newBuilder().build();
          ComponentProvisioningConfig componentProvisioningConfig =
              ComponentProvisioningConfig.newBuilder().build();

          // Call the method
          JsonObject jsonObject =
              ComponentUtil.componentConfigToJson(
                  componentDefinition, componentProvisioningConfig, EMPTY_JSON);

          // Assertions
          assertThat(jsonObject).isNotNull();
          assertTrue(jsonObject.containsKey(Constants.COMPONENT_CONFIG_KEY));
          assertTrue(jsonObject.containsKey(Constants.PROVISIONING_CONFIG_KEY));
          assertEquals("{}", jsonObject.getJsonObject(Constants.COMPONENT_CONFIG_KEY).toString());
          assertEquals(
              "{}", jsonObject.getJsonObject(Constants.PROVISIONING_CONFIG_KEY).toString());
        });
  }

  @Test
  void shouldReturnProvisioningConfigWhenFound() {
    // Mock input data
    String componentName = "Component1";
    ComponentProvisioningConfig matchingConfig =
        ComponentProvisioningConfig.newBuilder().setComponentName(componentName).build();
    List<ComponentProvisioningConfig> componentProvisioningConfigs =
        Arrays.asList(
            matchingConfig,
            ComponentProvisioningConfig.newBuilder().setComponentName("Component2").build());

    // Call the method
    ComponentProvisioningConfig result =
        ComponentUtil.getComponentProvisioningConfig(componentProvisioningConfigs, componentName);

    // Assertions
    assertThat(result).isNotNull();
    assertSame(matchingConfig, result);
  }

  @Test
  void shouldReturnNullWhenProvisioningConfigNotFound() {
    // Mock input data
    String componentName = "NonExistentComponent";
    List<ComponentProvisioningConfig> componentProvisioningConfigs =
        Arrays.asList(
            ComponentProvisioningConfig.newBuilder().setComponentName("Component1").build(),
            ComponentProvisioningConfig.newBuilder().setComponentName("Component2").build());

    // Call the method
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ComponentUtil.getComponentProvisioningConfig(
                componentProvisioningConfigs, componentName));
  }

  @Test
  void testGetComponentDefinitionFromJson() {
    // Mock input data
    JsonObject jsonObject = new JsonObject();
    jsonObject.put("name", "ComponentName");
    jsonObject.put("version", "1.0.0");

    // Call the method
    ComponentDefinition componentDefinition =
        ComponentUtil.getComponentDefinitionFromJson(jsonObject);

    // Assertions
    assertThat(componentDefinition).isNotNull();
    assertEquals("ComponentName", componentDefinition.getName());
    assertEquals("1.0.0", componentDefinition.getVersion());
  }

  @Test
  void testGetComponentProvisioningConfigFromJson() {
    // Mock input data
    JsonObject jsonObject = new JsonObject();
    jsonObject.put("componentName", "ComponentName");
    jsonObject.put("deploymentType", "Type1");

    ComponentProvisioningConfig componentProvisioningConfig =
        ComponentUtil.getComponentProvisioningConfigFromJson(jsonObject);

    // Assertions
    assertThat(componentProvisioningConfig).isNotNull();
    assertEquals("ComponentName", componentProvisioningConfig.getComponentName());
    assertEquals("Type1", componentProvisioningConfig.getDeploymentType());
  }

  @Test
  void testGetAllComponentsData() {
    // Mock input data
    ComponentDefinition component1 = ComponentDefinition.newBuilder().setName("component1").build();
    ComponentDefinition component2 = ComponentDefinition.newBuilder().setName("component2").build();
    ComponentProvisioningConfig provisioningConfig1 =
        ComponentProvisioningConfig.newBuilder()
            .setComponentName("component1")
            .setDeploymentType("aws_ec2")
            .build();
    ComponentProvisioningConfig provisioningConfig2 =
        ComponentProvisioningConfig.newBuilder()
            .setComponentName("component2")
            .setDeploymentType("aws_ec2")
            .build();
    List<ComponentDefinition> componentsList = Arrays.asList(component1, component2);
    List<ComponentProvisioningConfig> provisioningConfigs =
        Arrays.asList(provisioningConfig1, provisioningConfig2);
    ServiceDefinition serviceDefinition =
        ServiceDefinition.newBuilder().addAllComponents(componentsList).build();
    ServiceData serviceData =
        ServiceData.builder()
            .serviceDefinition(serviceDefinition)
            .componentProvisioningConfigs(provisioningConfigs)
            .build();
    Action action = Action.DEPLOY;

    AccountInformation accountInformation =
        AccountInformation.newBuilder()
            .setProviderAccountName("stag")
            .setServiceAccountsSnapshot(
                GetProviderAccountResponse.newBuilder()
                    .setAccount(ProviderAccount.newBuilder().setProvider("AWS").build())
                    .build())
            .build();
    List<AccountInformation> accountInformationList = List.of(accountInformation);

    // Call the method
    Map<ComponentId, ComponentData> result =
        ComponentUtil.getAllComponentsData(
            serviceData,
            accountInformationList,
            Map.of("component1", Action.DEPLOY, "component2", Action.DEPLOY));

    // Assertions
    assertThat(result).isNotNull();
    assertEquals(2, result.size());
    assertTrue(
        result.containsKey(
            ComponentId.builder().componentName("component1").action(action).build()));
    assertTrue(
        result.containsKey(
            ComponentId.builder().componentName("component2").action(action).build()));
    assertEquals(
        component1,
        result
            .get(ComponentId.builder().componentName("component1").action(action).build())
            .getComponentDefinition());
    assertEquals(
        accountInformation,
        result
            .get(ComponentId.builder().componentName("component1").action(action).build())
            .getEnvironmentProviderAccounts());
  }

  @Test
  void testGetComponentId() {
    // Mock input data
    String componentName = "Component1";
    Action action = Action.DEPLOY;

    // Call the method
    ComponentId componentId = ComponentUtil.buildComponentId(componentName, action);

    // Assertions
    assertThat(componentId).isNotNull();
    assertEquals(componentName, componentId.getComponentName());
    assertEquals(action, componentId.getAction());
  }

  @Test
  void testGetUnexecutedComponentNameSet() {
    // Mock input data
    JsonObject configObject = new JsonObject();
    configObject.put("name", "Component1");
    configObject.put("version", "1.0");
    configObject.put("type", "type1");

    // Create depends_on array
    JsonArray dependsOnArray = new JsonArray();
    dependsOnArray.add("Component3");

    configObject.put("depends_on", dependsOnArray);

    ComponentTaskEntity taskEntity1 =
        ComponentTaskEntity.builder()
            .componentName("Component1")
            .status(TaskStatus.SUCCESSFUL)
            .config(new JsonObject().put("componentConfig", configObject))
            .build();
    ComponentTaskEntity taskEntity2 =
        ComponentTaskEntity.builder()
            .componentName("Component2")
            .config(new JsonObject().put("componentConfig", new JsonObject()))
            .status(TaskStatus.SUCCESSFUL)
            .build();
    ComponentTaskEntity taskEntity3 =
        ComponentTaskEntity.builder()
            .componentName("Component3")
            .config(new JsonObject().put("componentConfig", new JsonObject()))
            .status(TaskStatus.FAILED)
            .build();

    List<ComponentTaskEntity> componentTaskEntities =
        Arrays.asList(taskEntity1, taskEntity2, taskEntity3);

    // Call the method
    Set<String> unexecutedComponents =
        ComponentUtil.getUnexecutedComponentNameSet(componentTaskEntities);

    // Assertions
    assertThat(unexecutedComponents).isNotNull();
    assertEquals(1, unexecutedComponents.size());
    assertTrue(unexecutedComponents.contains("Component1"));
  }
}
