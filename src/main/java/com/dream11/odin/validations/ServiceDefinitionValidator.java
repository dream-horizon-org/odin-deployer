package com.dream11.odin.validations;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.dto.v1.ComponentDefinition;
import com.dream11.odin.dto.v1.ServiceDefinition;
import com.dream11.odin.error.OdinError;
import com.dream11.odin.util.ServiceUtil;
import com.dream11.odin.util.SingleUtil;
import io.reactivex.Completable;
import io.reactivex.Single;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

@AllArgsConstructor
public class ServiceDefinitionValidator extends Validator {

  private Map<String, List<String>> adjacencyList = new HashMap<>();
  ServiceDefinition serviceDefinition;

  public ServiceDefinitionValidator(ServiceDefinition serviceDefinition) {
    this.serviceDefinition = serviceDefinition;
    serviceDefinition
        .getComponentsList()
        .forEach(
            componentDefinition ->
                this.adjacencyList
                    .computeIfAbsent(componentDefinition.getName(), k -> new ArrayList<>())
                    .addAll(componentDefinition.getDependsOnList()));
  }

  @Override
  @SneakyThrows
  public Completable validate() {
    return SingleUtil.toSingle(
            this.schemaParser
                .parse(ServiceUtil.readServiceDefinitionSchema())
                .validateAsync(ServiceUtil.serviceDefinitionToJsonObject(serviceDefinition)))
        .onErrorResumeNext(
            err ->
                Single.error(
                    ExceptionUtil.getException(
                        OdinError.INVALID_SERVICE_DEFINITION_FILE, err.getMessage())))
        .ignoreElement()
        .andThen(checkCyclicDependency())
        .andThen(checkDuplicateComponentNames());
  }

  private Completable checkDuplicateComponentNames() {
    long distinctComponentsCount =
        serviceDefinition.getComponentsList().stream()
            .map(ComponentDefinition::getName)
            .distinct()
            .count();
    return Completable.fromAction(
        () -> {
          if (distinctComponentsCount < serviceDefinition.getComponentsList().size()) {
            throw ExceptionUtil.getException(
                OdinError.INVALID_SERVICE_DEFINITION_FILE, Constants.COMPONENT_NAME_UNIQUE);
          }
        });
  }

  private Completable checkCyclicDependency() {
    if (hasCyclicDependency()) {
      return Completable.error(
          ExceptionUtil.getException(OdinError.CYCLIC_DEPENDENCY_PRESENT_IN_SERVICE_DEFINITION));
    }
    return Completable.complete();
  }

  private boolean hasCyclicDependency() {
    Set<String> visited = new HashSet<>();
    Set<String> recursionStack = new HashSet<>();

    for (String vertex : adjacencyList.keySet()) {
      if (recurseVertex(vertex, visited, recursionStack)) {
        return true;
      }
    }

    return false;
  }

  private boolean recurseVertex(String vertex, Set<String> visited, Set<String> recursionStack) {
    if (!visited.contains(vertex)) {
      visited.add(vertex);
      recursionStack.add(vertex);

      List<String> neighbors = adjacencyList.getOrDefault(vertex, Collections.emptyList());
      for (String neighbor : neighbors) {
        if ((!visited.contains(neighbor) && recurseVertex(neighbor, visited, recursionStack))
            || recursionStack.contains(neighbor)) {
          return true;
        }
      }
    }

    recursionStack.remove(vertex);
    return false;
  }
}
