package com.dream11.odin.validations;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.dto.v1.ComponentDefinition;
import com.dream11.odin.dto.v1.ComponentProvisioningConfig;
import com.dream11.odin.dto.v1.ServiceDefinition;
import com.dream11.odin.error.OdinError;
import io.reactivex.Completable;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class ComponentProvisioningMappingValidator extends Validator {

  ServiceDefinition serviceDefinition;
  List<ComponentProvisioningConfig> componentProvisioningConfigs;

  @Override
  public Completable validate() {
    Set<String> componentNames =
        serviceDefinition.getComponentsList().stream()
            .map(ComponentDefinition::getName)
            .collect(Collectors.toSet());
    Set<String> provComponentNames =
        componentProvisioningConfigs.stream()
            .map(ComponentProvisioningConfig::getComponentName)
            .collect(Collectors.toSet());

    return Completable.fromAction(
        () -> {
          if (provComponentNames.size() < componentProvisioningConfigs.size()) {
            throw ExceptionUtil.getException(
                OdinError.INVALID_PROVISIONING_CONFIG_FILE, Constants.COMPONENT_NAME_UNIQUE);
          }

          String invalidComponentNames =
              provComponentNames.stream()
                  .filter(componentName -> !componentNames.contains(componentName))
                  .collect(Collectors.joining(","));
          if (!invalidComponentNames.isEmpty()) {
            throw ExceptionUtil.getException(
                OdinError.INVALID_COMPONENT_IN_PROVISIONING_FILE, invalidComponentNames);
          }

          String missingComponentNames =
              componentNames.stream()
                  .filter(componentName -> !provComponentNames.contains(componentName))
                  .collect(Collectors.joining(","));
          if (!missingComponentNames.isEmpty()) {
            throw ExceptionUtil.getException(
                OdinError.PROVISIONING_CONFIG_NOT_FOUND, missingComponentNames);
          }
        });
  }
}
