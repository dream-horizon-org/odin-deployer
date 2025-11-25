package com.dream11.odin.entity;

import java.util.List;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class EnvironmentServiceEntityWithComponents {

  EnvironmentServiceEntity environmentServiceEntity;

  List<ComponentEntity> components;

  public EnvironmentServiceEntityWithComponents updateComponents(
      List<ComponentEntity> componentEntities) {
    this.components = componentEntities;
    return this;
  }

  public EnvironmentServiceEntityWithComponents updateEnvironmentServiceEntity(
      EnvironmentServiceEntity environmentServiceEntity) {
    this.environmentServiceEntity = environmentServiceEntity;
    return this;
  }
}
