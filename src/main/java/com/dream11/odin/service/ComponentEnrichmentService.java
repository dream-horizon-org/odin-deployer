package com.dream11.odin.service;

import com.dream11.odin.constant.Action;
import com.dream11.odin.dao.ComponentTaskDao;
import com.dream11.odin.dto.ComponentData;
import com.dream11.odin.dto.ComponentDataStatus;
import com.dream11.odin.dto.ComponentId;
import com.dream11.odin.dto.RequestMetaContext;
import com.dream11.odin.util.ComponentUtil;
import com.google.inject.Inject;
import io.reactivex.Single;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
}
