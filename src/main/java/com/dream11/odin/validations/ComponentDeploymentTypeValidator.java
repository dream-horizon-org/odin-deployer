package com.dream11.odin.validations;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.dto.v1.AccountInformation;
import com.dream11.odin.dto.v1.ComponentProvisioningConfig;
import com.dream11.odin.dto.v1.ProvisioningConfig;
import com.dream11.odin.error.OdinError;
import com.dream11.odin.util.EnvironmentUtil;
import io.reactivex.Completable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class ComponentDeploymentTypeValidator extends Validator {

  ProvisioningConfig provisioningConfig;
  List<AccountInformation> accountInformationList;
  String envName;

  @Override
  public Completable validate() {
    return Completable.fromAction(
        () -> {
          List<String> providers =
              EnvironmentUtil.getProviders(accountInformationList).stream()
                  .map(String::toLowerCase)
                  .toList();

          // Validate component name
          Map<String, String> invalidComponentsMap = new HashMap<>();
          for (ComponentProvisioningConfig componentProvisioningConfig :
              provisioningConfig.getComponentProvisioningConfigList()) {

            String deploymentType = componentProvisioningConfig.getDeploymentType();
            String deploymentTypePrefix = deploymentType.split("_")[0];

            // Validate deployment type prefix
            if (!providers.contains(deploymentTypePrefix)) {
              invalidComponentsMap.putIfAbsent(
                  componentProvisioningConfig.getComponentName(),
                  String.format(
                      "Invalid deploymentType [%s]. DeploymentType must start with [%s]",
                      componentProvisioningConfig.getDeploymentType(),
                      String.join(",", providers)));
            }
          }

          if (!invalidComponentsMap.isEmpty()) {
            throw ExceptionUtil.getException(
                OdinError.INVALID_COMPONENT_DEPLOYMENT_TYPE, invalidComponentsMap);
          }
        });
  }
}
