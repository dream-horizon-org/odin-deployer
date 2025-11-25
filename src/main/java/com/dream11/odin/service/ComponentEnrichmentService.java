package com.dream11.odin.service;

import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.dao.ComponentTaskDao;
import com.dream11.odin.dto.ComponentData;
import com.dream11.odin.dto.ComponentDataStatus;
import com.dream11.odin.dto.ComponentId;
import com.dream11.odin.dto.RequestMetaContext;
import com.dream11.odin.dto.v1.ProviderAccount;
import com.dream11.odin.dto.v1.ProviderServiceAccount;
import com.dream11.odin.util.ComponentUtil;
import com.dream11.odin.util.JsonUtil;
import com.google.inject.Inject;
import com.google.protobuf.Struct;
import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ComponentEnrichmentService {

  final ComponentTaskDao componentTaskDao;

  public Single<Map<ComponentId, ComponentData>> enrichComponentsFromDatabase(
      Map<ComponentId, ComponentData> componentDataMap, RequestMetaContext requestMetaContext) {

    List<Single<AbstractMap.SimpleEntry<ComponentId, ComponentData>>> singles =
        componentDataMap.entrySet().stream()
            .map(
                entry -> {
                  if (entry.getValue().getEnvironmentProviderAccounts() == null) {
                    return addAccountInformationToComponentData(
                            entry.getValue(), requestMetaContext)
                        .map(
                            componentDataStatus ->
                                new AbstractMap.SimpleEntry<>(
                                    entry.getKey(), componentDataStatus.getComponentData()));
                  } else {
                    return Single.just(
                        new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
                  }
                })
            .toList();

    return Single.merge(singles)
        .collect(HashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()));
  }

  /** Add account information to component data from previous task */
  public Single<ComponentDataStatus> addAccountInformationToComponentData(
      ComponentData componentData, RequestMetaContext requestMetaContext) {
    if (componentData.getEnvironmentProviderAccounts() != null) {
      return Single.just(
          ComponentDataStatus.builder().componentData(componentData).action(Action.DEPLOY).build());
    }
    return componentTaskDao
        .getLatestSuccessfulDeployOrOperateComponentTask(
            requestMetaContext.getEnvironment().getId(),
            requestMetaContext.getServiceName(),
            componentData.getComponentDefinition().getName())
        .map(
            componentTaskEntity -> {
              ComponentData oldComponentData =
                  ComponentUtil.getComponentData(
                      componentTaskEntity); // Construct older component data from task
              return ComponentDataStatus.builder()
                  .componentData(
                      ComponentData.builder()
                          .componentDefinition(oldComponentData.getComponentDefinition())
                          .componentProvisioningConfig(
                              oldComponentData.getComponentProvisioningConfig())
                          .operationConfig(componentData.getOperationConfig())
                          .environmentProviderAccounts(
                              oldComponentData.getEnvironmentProviderAccounts())
                          .build())
                  .action(componentTaskEntity.getAction())
                  .status(componentTaskEntity.getStatus())
                  .build();
            });
  }

  public Map<ComponentId, ComponentData> addOdinDiscoveryData(
      Map<ComponentId, ComponentData> componentDataMap) {
    return componentDataMap.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, entry -> this.addOdinDiscoveryData(entry.getValue())));
  }

  public ComponentData addOdinDiscoveryData(ComponentData componentData) {
    // Get linked account
    List<ProviderAccount> linkedAccountsList =
        componentData
            .getEnvironmentProviderAccounts()
            .getServiceAccountsSnapshot()
            .getLinkedAccountsList();

    Optional<ProviderAccount> odinAccount =
        linkedAccountsList.stream()
            .filter(
                account -> account.getProvider().equalsIgnoreCase(Constants.ODIN_ACCOUNT_PROVIDER))
            .findFirst();
    if (odinAccount.isEmpty()) {
      // Odin account not found
      log.debug("Account with odin provider not found");
      return componentData;
    }

    Optional<ProviderServiceAccount> odinDiscoveryServiceAccount =
        odinAccount.get().getServicesList().stream()
            .filter(
                service ->
                    service
                        .getCategory()
                        .equals(Constants.ODIN_DISCOVERY_PROVIDER_SERVICE_CATEGORY))
            .findFirst();

    if (odinDiscoveryServiceAccount.isEmpty()) {
      // Odin discovery service not found
      log.debug("Odin discovery service not found");
      return componentData;
    }

    JsonObject componentConfig =
        JsonUtil.getJsonFromProto(componentData.getComponentDefinition().getConfig());
    if (!componentConfig.containsKey(Constants.DISCOVERY_CONFIG_KEY)) {
      // No discovery config found
      log.debug("No discovery config in definition");
      return componentData;
    }
    JsonObject odinDiscoveryData =
        JsonObject.of(
            Constants.DISCOVERY_ANNOTATIONS_KEY,
            this.buildDiscoveryData(componentConfig.getJsonObject(Constants.DISCOVERY_CONFIG_KEY)));
    log.info(
        "Adding odin discovery data :{} for component:{}",
        odinDiscoveryData,
        componentData.getComponentDefinition().getName());
    List<ProviderServiceAccount> updatedProviderServiceAccountList =
        odinAccount.get().getServicesList().stream()
            .map(
                service -> {
                  if (service
                      .getCategory()
                      .equals(Constants.ODIN_DISCOVERY_PROVIDER_SERVICE_CATEGORY)) {
                    service =
                        service.toBuilder()
                            .setData(
                                service.getData().toBuilder()
                                    .mergeFrom(
                                        JsonUtil.jsonToProtoBuilder(
                                                odinDiscoveryData, Struct.newBuilder())
                                            .build())
                                    .build())
                            .build();
                  }
                  return service;
                })
            .toList();

    List<ProviderAccount> updatedLinkedAccountsList =
        linkedAccountsList.stream()
            .map(
                account -> {
                  if (account.getProvider().equalsIgnoreCase(Constants.ODIN_ACCOUNT_PROVIDER)) {
                    account =
                        account.toBuilder()
                            .clearServices()
                            .addAllServices(updatedProviderServiceAccountList)
                            .build();
                  }
                  return account;
                })
            .toList();

    return ComponentData.builder()
        .operationConfig(componentData.getOperationConfig())
        .componentDefinition(componentData.getComponentDefinition())
        .componentProvisioningConfig(componentData.getComponentProvisioningConfig())
        .stageConfig(componentData.getStageConfig())
        .environmentProviderAccounts(
            componentData.getEnvironmentProviderAccounts().toBuilder()
                .setServiceAccountsSnapshot(
                    componentData
                        .getEnvironmentProviderAccounts()
                        .getServiceAccountsSnapshot()
                        .toBuilder()
                        .clearLinkedAccounts()
                        .addAllLinkedAccounts(updatedLinkedAccountsList)
                        .build())
                .build())
        .build();
  }

  private JsonObject buildDiscoveryData(JsonObject config) {
    JsonObject discoveryData = new JsonObject();

    for (String key : config.fieldNames()) {
      Object value = config.getValue(key);

      if (value instanceof JsonObject jsonObject) {
        discoveryData.put(key, this.buildDiscoveryData(jsonObject));
      } else if (value instanceof String || value instanceof JsonArray) {
        // Assumes lists are only present in leaf nodes
        discoveryData.put(key, JsonObject.of(Constants.ODIN_DISCOVERY_ANNOTATION, value));
      } else {
        discoveryData.put(key, value);
      }
    }
    return discoveryData;
  }
}
