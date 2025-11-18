package com.dream11.odin.service;

import static com.dream11.odin.constant.Constants.ODIN_COMPONENT_NAME;
import static com.dream11.odin.constant.Constants.ODIN_COMPONENT_TYPE;
import static com.dream11.odin.constant.Constants.ODIN_COMPONENT_VERSION;
import static com.dream11.odin.constant.Constants.ODIN_ENV_NAME;
import static com.dream11.odin.constant.Constants.ODIN_RESOURCE_TYPE;
import static com.dream11.odin.constant.Constants.ODIN_SERVICE_NAME;
import static com.dream11.odin.constant.Constants.ODIN_USER;

import com.dream11.odin.dto.ComponentData;
import com.dream11.odin.dto.ComponentIdentifier;
import com.dream11.odin.dto.RequestMetaContext;
import com.dream11.odin.dto.v1.AccountInformation;
import com.dream11.odin.dto.v1.ProviderAccount;
import com.dream11.odin.dto.v1.ProviderServiceAccount;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountResponse;
import com.dream11.odin.util.JsonUtil;
import com.google.inject.Inject;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.reactivex.Single;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

// TODO We need to relook at the placeholder service design again. Right now it has a logic lifted
// and shifted from old plugin system as is.
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class PlaceholderService {

  final ComponentEnrichmentService componentEnrichmentService;

  /** Main method to replace placeholders in a batch of components */
  public Single<Map<ComponentIdentifier, ComponentData>> replacePlaceholdersInComponents(
      Map<ComponentIdentifier, ComponentData> componentDataMap,
      RequestMetaContext requestMetaContext) {

    List<Single<Map.Entry<ComponentIdentifier, ComponentData>>> singles = new ArrayList<>();

    componentDataMap.forEach(
        (key, value) -> {
          Single<Map.Entry<ComponentIdentifier, ComponentData>> single =
              replacePlaceholders(value, requestMetaContext)
                  .map(
                      updatedComponentData ->
                          new AbstractMap.SimpleEntry<>(key, updatedComponentData));
          singles.add(single);
        });

    return Single.zip(
        singles,
        results -> {
          Map<ComponentIdentifier, ComponentData> resultMap = new HashMap<>();
          Set<String> removedComponents = new HashSet<>();
          for (Object result : results) {
            @SuppressWarnings("unchecked")
            Map.Entry<ComponentIdentifier, ComponentData> entry =
                (Map.Entry<ComponentIdentifier, ComponentData>) result;
            if (!Objects.isNull(entry.getValue().getComponentDefinition())) {
              resultMap.put(entry.getKey(), entry.getValue());
            } else {
              removedComponents.add(entry.getKey().getComponentName());
            }
          }
          resultMap.forEach(
              (key, value) -> {
                List<String> modifiableList =
                    new ArrayList<>(value.getComponentDefinition().getDependsOnList());
                modifiableList.removeAll(removedComponents);
                value.setComponentDefinition(
                    value.getComponentDefinition().toBuilder()
                        .clearDependsOn()
                        .addAllDependsOn(modifiableList)
                        .build());
              });
          return resultMap;
        });
  }

  /** Replace placeholders in a single component */
  public Single<ComponentData> replacePlaceholders(
      ComponentData componentData, RequestMetaContext requestMetaContext) {

    if (componentData.getOperationConfig() != null) {
      return componentEnrichmentService
          .addAccountInformationToComponentData(componentData, requestMetaContext)
          .map(
              oldComponentDataStatus -> {
                ComponentData oldComponentData = oldComponentDataStatus.getComponentData();
                return ComponentData.builder()
                    .componentDefinition(oldComponentData.getComponentDefinition())
                    .componentProvisioningConfig(oldComponentData.getComponentProvisioningConfig())
                    .operationConfig(componentData.getOperationConfig())
                    .environmentProviderAccounts(oldComponentData.getEnvironmentProviderAccounts())
                    .build();
              })
          .map(updatedComponentData -> applyPlaceholders(updatedComponentData, requestMetaContext));
    }

    return Single.just(applyPlaceholders(componentData, requestMetaContext));
  }

  /** Apply placeholder replacements to component data */
  private ComponentData applyPlaceholders(
      ComponentData componentData, RequestMetaContext requestMetaContext) {

    // Gather all placeholders (Odin + ConfigSuite)
    Map<String, String> allPlaceholders = new HashMap<>();
    allPlaceholders.putAll(getOdinPlaceholders(componentData, requestMetaContext));
    allPlaceholders.putAll(getConfigSuitePlaceholders(componentData));

    if (allPlaceholders.isEmpty()) {
      return componentData;
    }

    // Replace in provider accounts
    AccountInformation.Builder accountBuilder =
        componentData.getEnvironmentProviderAccounts().toBuilder();
    GetProviderAccountResponse.Builder serviceAccountSnapshotBuilder =
        accountBuilder.getServiceAccountsSnapshotBuilder();
    GetProviderAccountResponse providerAccountResponse =
        replacePlaceholdersInProviderAccounts(allPlaceholders, serviceAccountSnapshotBuilder);

    // Replace in component configs
    Struct config =
        JsonUtil.replaceStructValues(
            componentData.getComponentDefinition().getConfig(), allPlaceholders);
    Struct params =
        JsonUtil.replaceStructValues(
            componentData.getComponentProvisioningConfig().getParams(), allPlaceholders);

    ComponentData.ComponentDataBuilder componentDataBuilder = ComponentData.builder();
    if (componentData.getOperationConfig() != null) {
      componentDataBuilder.operationConfig(
          Struct.newBuilder()
              .putAllFields(
                  JsonUtil.replaceStructValues(componentData.getOperationConfig(), allPlaceholders)
                      .getFieldsMap())
              .build());
    }

    return componentDataBuilder
        .componentDefinition(
            componentData.getComponentDefinition().toBuilder().setConfig(config).build())
        .componentProvisioningConfig(
            componentData.getComponentProvisioningConfig().toBuilder().setParams(params).build())
        .environmentProviderAccounts(
            accountBuilder
                .setServiceAccountsSnapshot(
                    serviceAccountSnapshotBuilder.setAccount(providerAccountResponse.getAccount()))
                .build())
        .build();
  }

  /** Replace placeholders in environment accounts */
  public Single<List<GetProviderAccountResponse>> replacePlaceholdersInEnvironment(
      List<GetProviderAccountResponse> accounts, RequestMetaContext requestMetaContext) {

    Struct odinPlaceholdersConfig =
        Struct.newBuilder()
            .putAllFields(
                Map.of(
                    "envName",
                    Value.newBuilder()
                        .setStringValue(requestMetaContext.getEnvironment().getName())
                        .build(),
                    "userEmail",
                    Value.newBuilder()
                        .setStringValue(requestMetaContext.getUserDetails().getEmailId())
                        .build()))
            .build();

    List<GetProviderAccountResponse> accountResult = new ArrayList<>();
    Map<String, String> placeholderAndValues = getEnvironmentPlaceholders(odinPlaceholdersConfig);

    accounts.forEach(
        accountResponse ->
            accountResult.add(
                replacePlaceholdersInProviderAccounts(
                    placeholderAndValues, accountResponse.toBuilder())));

    return Single.just(accountResult);
  }

  /** Get Odin system placeholders */
  private Map<String, String> getOdinPlaceholders(
      ComponentData componentData, RequestMetaContext requestMetaContext) {
    Map<String, String> placeholders = new HashMap<>();
    placeholders.put(ODIN_COMPONENT_NAME, componentData.getComponentDefinition().getName());
    placeholders.put(ODIN_ENV_NAME, requestMetaContext.getEnvironment().getName());
    placeholders.put(ODIN_SERVICE_NAME, requestMetaContext.getServiceName());
    placeholders.put(ODIN_COMPONENT_VERSION, componentData.getComponentDefinition().getVersion());
    placeholders.put(ODIN_COMPONENT_TYPE, componentData.getComponentDefinition().getType());
    placeholders.put(ODIN_RESOURCE_TYPE, componentData.getComponentDefinition().getType());
    placeholders.put(ODIN_USER, requestMetaContext.getUserDetails().getEmailId().split("@")[0]);
    return placeholders;
  }

  /** Get ConfigSuite placeholders */
  private Map<String, String> getConfigSuitePlaceholders(ComponentData componentData) {
    Map<String, String> placeholderAndValues = new HashMap<>();

    componentData
        .getEnvironmentProviderAccounts()
        .getServiceAccountsSnapshot()
        .getAccount()
        .getServicesList()
        .stream()
        .filter(service -> "ConfigSuite".equals(service.getName()))
        .findFirst()
        .map(ProviderServiceAccount::getData)
        .ifPresent(
            data -> {
              if (data.getFieldsMap().containsKey("placeholder")) {
                Struct placeholderStruct = data.getFieldsOrThrow("placeholder").getStructValue();
                placeholderStruct
                    .getFieldsMap()
                    .forEach(
                        (key, value) -> {
                          if (value.hasStringValue()) {
                            placeholderAndValues.put(key, value.getStringValue());
                          }
                        });
              }
            });
    return placeholderAndValues;
  }

  /** Get environment-level placeholders */
  private Map<String, String> getEnvironmentPlaceholders(Struct config) {
    Map<String, String> placeholders = new HashMap<>();
    placeholders.put(ODIN_COMPONENT_NAME, StringUtils.EMPTY);
    placeholders.put(ODIN_SERVICE_NAME, StringUtils.EMPTY);
    placeholders.put(ODIN_ENV_NAME, config.getFieldsOrThrow("envName").getStringValue());
    placeholders.put(ODIN_COMPONENT_TYPE, StringUtils.EMPTY);
    placeholders.put(ODIN_RESOURCE_TYPE, StringUtils.EMPTY);
    placeholders.put(
        ODIN_USER, config.getFieldsOrThrow("userEmail").getStringValue().split("@")[0]);
    return placeholders;
  }

  /** Replace placeholders in provider accounts */
  private GetProviderAccountResponse replacePlaceholdersInProviderAccounts(
      Map<String, String> placeholderAndValues,
      GetProviderAccountResponse.Builder serviceAccountSnapshotBuilder) {

    ProviderAccount.Builder accountBuilder = serviceAccountSnapshotBuilder.getAccountBuilder();
    Struct accountData =
        JsonUtil.replaceStructValues(accountBuilder.getData(), placeholderAndValues);

    List<ProviderServiceAccount.Builder> serviceBuilders = new ArrayList<>();

    for (ProviderServiceAccount.Builder serviceBuilder : accountBuilder.getServicesBuilderList()) {
      Struct data = JsonUtil.replaceStructValues(serviceBuilder.getData(), placeholderAndValues);
      serviceBuilders.add(serviceBuilder.setData(data));
    }

    accountBuilder.clearServices();
    serviceBuilders.forEach(accountBuilder::addServices);
    return serviceAccountSnapshotBuilder
        .setAccount(accountBuilder.setData(accountData).build())
        .build();
  }
}
