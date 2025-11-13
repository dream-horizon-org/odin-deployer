package com.dream11.odin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dream11.odin.constant.Action;
import com.dream11.odin.dao.ComponentTaskDao;
import com.dream11.odin.dto.ComponentData;
import com.dream11.odin.dto.ComponentDataStatus;
import com.dream11.odin.dto.ComponentId;
import com.dream11.odin.dto.RequestMetaContext;
import com.dream11.odin.dto.UserDetails;
import com.dream11.odin.dto.v1.AccountInformation;
import com.dream11.odin.dto.v1.ComponentDefinition;
import com.dream11.odin.dto.v1.ComponentProvisioningConfig;
import com.dream11.odin.dto.v1.Environment;
import com.dream11.odin.entity.ComponentTaskEntity;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ComponentEnrichmentServiceTest {

  @Mock private ComponentTaskDao componentTaskDao;

  private ComponentEnrichmentService componentEnrichmentService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    componentEnrichmentService = new ComponentEnrichmentService(componentTaskDao);
  }

  /**
   * Test that database lookup is skipped when component already has account information. Verifies
   * that the service does not query the database unnecessarily.
   */
  @Test
  void shouldSkipDatabaseLookupWhenComponentHasExistingAccountInfo() {
    // Arrange
    ComponentData componentData =
        ComponentData.builder()
            .componentDefinition(ComponentDefinition.newBuilder().setName("test-component").build())
            .componentProvisioningConfig(
                ComponentProvisioningConfig.newBuilder().setComponentName("test-component").build())
            .environmentProviderAccounts(
                AccountInformation.newBuilder().build()) // Already has account info
            .build();

    ComponentId componentId =
        ComponentId.builder().componentName("test-component").action(Action.OPERATE).build();

    Map<ComponentId, ComponentData> componentDataMap = new HashMap<>();
    componentDataMap.put(componentId, componentData);

    RequestMetaContext requestMetaContext = buildTestComponentContext();

    // Act
    Map<ComponentId, ComponentData> result =
        componentEnrichmentService
            .enrichComponentsFromDatabase(componentDataMap, requestMetaContext)
            .blockingGet();

    // Assert
    assertThat(result).hasSize(1).containsEntry(componentId, componentData);
    verify(componentTaskDao, never())
        .getLatestSuccessfulDeployOrOperateComponentTask(anyLong(), anyString(), anyString());
  }

  /**
   * Test that component account information is fetched from database when not present. Verifies
   * that the service queries the database for missing account information.
   */
  @Test
  void shouldFetchFromDatabaseWhenComponentLacksAccountInfo() {
    // Arrange
    ComponentData componentData =
        ComponentData.builder()
            .componentDefinition(ComponentDefinition.newBuilder().setName("test-component").build())
            .componentProvisioningConfig(
                ComponentProvisioningConfig.newBuilder().setComponentName("test-component").build())
            .environmentProviderAccounts(null) // No account info
            .build();

    ComponentId componentId =
        ComponentId.builder().componentName("test-component").action(Action.OPERATE).build();

    Map<ComponentId, ComponentData> componentDataMap = new HashMap<>();
    componentDataMap.put(componentId, componentData);

    RequestMetaContext requestMetaContext = buildTestComponentContext();

    when(componentTaskDao.getLatestSuccessfulDeployOrOperateComponentTask(
            anyLong(), anyString(), anyString()))
        .thenReturn(Single.just(buildMockComponentTaskEntity()));

    // Act
    Map<ComponentId, ComponentData> result =
        componentEnrichmentService
            .enrichComponentsFromDatabase(componentDataMap, requestMetaContext)
            .blockingGet();

    // Assert
    assertThat(result).hasSize(1);
    verify(componentTaskDao, times(1))
        .getLatestSuccessfulDeployOrOperateComponentTask(
            requestMetaContext.getEnvironment().getId(),
            requestMetaContext.getServiceName(),
            "test-component");
  }

  /**
   * Test that only components without account information are enriched in a mixed scenario.
   * Verifies selective enrichment based on missing account information.
   */
  @Test
  void shouldEnrichOnlyComponentsWithoutAccountInfoWhenMixedScenario() {
    // Arrange
    ComponentData componentWithAccount =
        ComponentData.builder()
            .componentDefinition(ComponentDefinition.newBuilder().setName("component-1").build())
            .environmentProviderAccounts(AccountInformation.newBuilder().build())
            .build();

    ComponentData componentWithoutAccount =
        ComponentData.builder()
            .componentDefinition(ComponentDefinition.newBuilder().setName("component-2").build())
            .environmentProviderAccounts(null)
            .build();

    ComponentId id1 =
        ComponentId.builder().componentName("component-1").action(Action.OPERATE).build();
    ComponentId id2 =
        ComponentId.builder().componentName("component-2").action(Action.OPERATE).build();

    Map<ComponentId, ComponentData> componentDataMap = new HashMap<>();
    componentDataMap.put(id1, componentWithAccount);
    componentDataMap.put(id2, componentWithoutAccount);

    RequestMetaContext requestMetaContext = buildTestComponentContext();

    when(componentTaskDao.getLatestSuccessfulDeployOrOperateComponentTask(
            anyLong(), anyString(), anyString()))
        .thenReturn(Single.just(buildMockComponentTaskEntity()));

    // Act
    Map<ComponentId, ComponentData> result =
        componentEnrichmentService
            .enrichComponentsFromDatabase(componentDataMap, requestMetaContext)
            .blockingGet();

    // Assert
    assertThat(result).hasSize(2);
    verify(componentTaskDao, times(1))
        .getLatestSuccessfulDeployOrOperateComponentTask(anyLong(), anyString(), anyString());
  }

  /**
   * Test that original component data is returned when account information exists. Verifies that no
   * database lookup is performed for components with existing account information.
   */
  @Test
  void shouldReturnOriginalComponentDataWhenAccountInformationExists() {
    // Arrange
    ComponentData componentData =
        ComponentData.builder()
            .componentDefinition(ComponentDefinition.newBuilder().setName("test-component").build())
            .environmentProviderAccounts(AccountInformation.newBuilder().build())
            .build();

    RequestMetaContext requestMetaContext = buildTestComponentContext();

    // Act
    ComponentDataStatus result =
        componentEnrichmentService
            .addAccountInformationToComponentData(componentData, requestMetaContext)
            .blockingGet();

    // Assert
    assertThat(result.getComponentData()).isEqualTo(componentData);
    assertThat(result.getAction()).isEqualTo(Action.DEPLOY);
    verify(componentTaskDao, never())
        .getLatestSuccessfulDeployOrOperateComponentTask(anyLong(), anyString(), anyString());
  }

  /**
   * Test that account information is fetched from database when missing from component data.
   * Verifies database query and account information enrichment.
   */
  @Test
  void shouldFetchAccountInformationFromDatabaseWhenAccountIsMissing() {
    // Arrange
    ComponentData componentData =
        ComponentData.builder()
            .componentDefinition(ComponentDefinition.newBuilder().setName("test-component").build())
            .environmentProviderAccounts(null)
            .build();

    RequestMetaContext requestMetaContext = buildTestComponentContext();

    ComponentTaskEntity mockEntity = buildMockComponentTaskEntity();
    when(componentTaskDao.getLatestSuccessfulDeployOrOperateComponentTask(
            anyLong(), anyString(), anyString()))
        .thenReturn(Single.just(mockEntity));

    // Act
    ComponentDataStatus result =
        componentEnrichmentService
            .addAccountInformationToComponentData(componentData, requestMetaContext)
            .blockingGet();

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getAction()).isEqualTo(mockEntity.getAction());
    verify(componentTaskDao, times(1))
        .getLatestSuccessfulDeployOrOperateComponentTask(
            requestMetaContext.getEnvironment().getId(),
            requestMetaContext.getServiceName(),
            "test-component");
  }

  /**
   * Helper method to build a test RequestMetaContext with default values for testing component
   * enrichment scenarios.
   */
  private RequestMetaContext buildTestComponentContext() {
    return RequestMetaContext.builder()
        .serviceName("test-service")
        .environment(Environment.newBuilder().setId(1L).setName("test-env").build())
        .userDetails(UserDetails.builder().userId("test-user").build())
        .additionalContext(Map.of("OPERATION", "restart"))
        .build();
  }

  /**
   * Helper method to build a mock ComponentTaskEntity with test data including component
   * definition, provisioning config, and account information.
   */
  private ComponentTaskEntity buildMockComponentTaskEntity() {
    JsonObject definition =
        new JsonObject()
            .put("name", "test-component")
            .put("version", "1.0.0")
            .put("type", "application")
            .put("depends_on", new io.vertx.core.json.JsonArray())
            .put("config", new JsonObject());

    JsonObject provisioning =
        new JsonObject()
            .put("component_name", "test-component")
            .put("deployment_type", "kubernetes")
            .put("params", new JsonObject());

    JsonObject accountInfo =
        new JsonObject().put("provider_account_name", "test-account").put("provider", "aws");

    JsonObject config =
        new JsonObject().put("componentConfig", definition).put("provisioningConfig", provisioning);

    return ComponentTaskEntity.builder()
        .componentName("test-component")
        .action(Action.DEPLOY)
        .status(com.dream11.odin.constant.TaskStatus.SUCCESSFUL)
        .config(config)
        .accounts(new JsonObject().put("aws", accountInfo))
        .build();
  }
}
