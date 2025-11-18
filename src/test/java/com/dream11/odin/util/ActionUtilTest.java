package com.dream11.odin.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dream11.odin.constant.Action;
import com.dream11.odin.dto.ComponentData;
import com.dream11.odin.dto.ComponentIdentifier;
import com.dream11.odin.dto.requestqueue.ComponentAction;
import com.dream11.odin.dto.v1.ComponentDefinition;
import com.dream11.odin.dto.v1.ComponentProvisioningConfig;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class ActionUtilTest {

  @Test
  void testBuildComponentActionNoData() {
    // Arrange
    Map<ComponentIdentifier, ComponentData> componentDataMap = new HashMap<>();
    Action stageName = Action.DEPLOY;
    Map<String, Object> stageConfig = new HashMap<>();

    // Act
    List<ComponentAction> result =
        ActionUtil.buildComponentActions(componentDataMap, stageName, stageConfig);

    // Assertions
    assertThat(result).isNotNull();
    assertEquals(0, result.size());
  }

  @Test
  void testBuildComponentActionValidate(Vertx vertx) {
    vertx.runOnContext(
        __ -> {
          // Arrange
          Map<ComponentIdentifier, ComponentData> componentDataMap = new HashMap<>();
          Action stageName = Action.VALIDATE;
          Map<String, Object> stageConfig = new HashMap<>();

          componentDataMap.put(
              ComponentIdentifier.builder()
                  .componentName("testComponent1")
                  .action(Action.VALIDATE)
                  .build(),
              ComponentData.builder()
                  .componentDefinition(
                      ComponentDefinition.newBuilder()
                          .setName("testComponent1")
                          .addDependsOn("testComponent2")
                          .getDefaultInstanceForType())
                  .componentProvisioningConfig(
                      ComponentProvisioningConfig.newBuilder()
                          .setComponentName("testComponent1")
                          .getDefaultInstanceForType())
                  .build());

          // Act
          List<ComponentAction> result =
              ActionUtil.buildComponentActions(componentDataMap, stageName, stageConfig);

          // Assertions
          assertThat(result).isNotNull();
          assertEquals(0, result.get(0).getDependsOn().size());
        });
  }

  @Test
  void testFilterSkippedDependencies() {
    // Mock input data
    List<ComponentAction> componentActions = new ArrayList<>();

    componentActions.add(
        ComponentAction.builder()
            .id(1)
            .componentName("ComponentA")
            .componentType("TypeA")
            .componentVersion("1.0.0")
            .deploymentType("TypeA")
            .baseConfig(new HashMap<>())
            .flavourConfig(new HashMap<>())
            .operationConfig(new HashMap<>())
            .dependsOn(new ArrayList<>())
            .build());

    componentActions.add(
        ComponentAction.builder()
            .id(2)
            .componentName("ComponentB")
            .componentType("TypeB")
            .componentVersion("1.0.0")
            .deploymentType("DTypeB")
            .baseConfig(new HashMap<>())
            .flavourConfig(new HashMap<>())
            .operationConfig(new HashMap<>())
            .dependsOn(List.of(1))
            .build());

    componentActions.add(
        ComponentAction.builder()
            .id(3)
            .componentName("ComponentC")
            .componentType("TypeC")
            .componentVersion("1.0.0")
            .deploymentType("DTypeC")
            .baseConfig(new HashMap<>())
            .flavourConfig(new HashMap<>())
            .operationConfig(new HashMap<>())
            .dependsOn(List.of(5))
            .build());

    // Call the method
    List<ComponentAction> result = ActionUtil.filterSkippedDependencies(componentActions);

    // Assertions
    assertThat(result).isNotNull();

    assertEquals(3, result.size());

    assertTrue(
        result.stream()
            .filter(componentAction -> componentAction.getId() == 3)
            .findFirst()
            .get()
            .getDependsOn()
            .isEmpty());
  }
}
