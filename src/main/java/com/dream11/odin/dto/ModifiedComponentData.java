package com.dream11.odin.dto;

import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.dto.requestqueue.ComponentAction;
import com.dream11.odin.dto.requestqueue.Stage;
import com.dream11.odin.dto.v1.AccountInformation;
import com.dream11.odin.dto.v1.ComponentDefinition;
import com.dream11.odin.dto.v1.ComponentProvisioningConfig;
import com.dream11.odin.entity.ComponentTaskEntity;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountResponse;
import com.dream11.odin.util.ComponentUtil;
import com.dream11.odin.util.JsonUtil;
import java.util.List;
import java.util.Map;
import lombok.Getter;

@Getter
public class ModifiedComponentData {
  final ComponentId deployComponentId;
  final ComponentId undeployComponentId;
  final ComponentData oldComponentData;
  final ComponentData newComponentData;
  final ComponentAction undeployComponentAction;
  final ComponentAction deployComponentAction;

  public ModifiedComponentData(
      ComponentTaskEntity componentTaskEntity,
      Map<ComponentId, ComponentData> componentDataMap,
      List<ComponentAction> componentActions) {
    this.deployComponentId =
        ComponentUtil.buildComponentId(componentTaskEntity.getComponentName(), Action.DEPLOY);
    this.undeployComponentId =
        ComponentUtil.buildComponentId(componentTaskEntity.getComponentName(), Action.UNDEPLOY);

    ComponentDefinition oldComponentDefinition =
        ComponentUtil.getComponentDefinitionFromJson(
            componentTaskEntity.getConfig().getJsonObject(Constants.COMPONENT_CONFIG_KEY));

    ComponentProvisioningConfig oldComponentProvisioningConfig =
        ComponentUtil.getComponentProvisioningConfigFromJson(
            componentTaskEntity.getConfig().getJsonObject(Constants.PROVISIONING_CONFIG_KEY));

    GetProviderAccountResponse providerAccountResponse =
        JsonUtil.jsonToProtoBuilder(
                componentTaskEntity.getAccounts(), GetProviderAccountResponse.newBuilder())
            .build();
    AccountInformation oldAccountInformation =
        AccountInformation.newBuilder()
            .setServiceAccountsSnapshot(providerAccountResponse)
            .setProviderAccountName(providerAccountResponse.getAccount().getName())
            .build();

    this.oldComponentData =
        ComponentData.builder()
            .componentDefinition(oldComponentDefinition)
            .componentProvisioningConfig(oldComponentProvisioningConfig)
            .environmentProviderAccounts(oldAccountInformation)
            .build();

    this.undeployComponentAction =
        ComponentUtil.getComponentAction(
            oldComponentDefinition,
            oldComponentProvisioningConfig,
            oldAccountInformation,
            Stage.builder().name(Action.UNDEPLOY).build());

    // If component task belong to a component which is not present in new service definition
    // but was a part of previous failed deployment. Then it's new configuration will not be present
    if (componentDataMap.containsKey(this.deployComponentId)) {
      this.newComponentData =
          ComponentData.builder()
              .componentDefinition(
                  componentDataMap.get(this.deployComponentId).getComponentDefinition())
              .componentProvisioningConfig(
                  componentDataMap.get(this.deployComponentId).getComponentProvisioningConfig())
              .environmentProviderAccounts(
                  componentDataMap.get(this.deployComponentId).getEnvironmentProviderAccounts())
              .build();
      this.deployComponentAction =
          ComponentUtil.getComponentAction(this.deployComponentId, componentActions)
              .addDependsOn(List.of(undeployComponentAction.getId()));
    } else {
      this.newComponentData = null;
      this.deployComponentAction = null;
    }
  }
}
