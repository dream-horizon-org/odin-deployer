package com.dream11.odin.validations;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.dto.v1.ComponentDefinition;
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
public class ComponentDependsOnValidator extends Validator {

  ServiceDefinition serviceDefinition;

  @Override
  public Completable validate() {
    Set<String> allDependsOnComponentNames =
        serviceDefinition.getComponentsList().stream()
            .map(componentDefinition -> componentDefinition.getDependsOnList().stream().toList())
            .flatMap(List::stream)
            .collect(Collectors.toSet());

    Set<String> allComponentNames =
        serviceDefinition.getComponentsList().stream()
            .map(ComponentDefinition::getName)
            .collect(Collectors.toSet());

    allDependsOnComponentNames.removeAll(allComponentNames);

    return Completable.fromAction(
        () -> {
          if (!allDependsOnComponentNames.isEmpty()) {
            throw ExceptionUtil.getException(OdinError.COMPONENT_NOT_FOUND);
          }
        });
  }
}
