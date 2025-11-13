package com.dream11.odin.dto.requestqueue;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.With;

@Data
@Builder
@Getter
public class ComponentAction {
  Integer id;

  @JsonProperty("name")
  String componentName;

  @JsonProperty("type")
  String componentType;

  @JsonProperty("version")
  String componentVersion;

  String deploymentType;
  Map<String, Object> baseConfig;
  Map<String, Object> flavourConfig;
  Map<String, Object> operationConfig;
  @With List<Integer> dependsOn;
  @With Stage stage;
  @With String provider;

  Map<String, Object> accounts;

  public ComponentAction addDependsOn(List<Integer> dependsOn) {
    List<Integer> currentDependencies =
        (this.getDependsOn() != null) ? this.getDependsOn() : new ArrayList<>();
    return ComponentAction.builder()
        .id(this.id)
        .componentName(this.componentName)
        .componentType(this.componentType)
        .componentVersion(this.componentVersion)
        .deploymentType(this.deploymentType)
        .baseConfig(this.baseConfig)
        .flavourConfig(this.flavourConfig)
        .operationConfig(this.operationConfig)
        .dependsOn(Stream.concat(dependsOn.stream(), currentDependencies.stream()).toList())
        .stage(this.stage)
        .provider(this.provider)
        .accounts(this.accounts)
        .build();
  }
}
