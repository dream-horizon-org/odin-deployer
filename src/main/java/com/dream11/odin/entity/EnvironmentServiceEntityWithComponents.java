package com.dream11.odin.entity;

import java.util.List;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class EnvironmentServiceEntityWithComponents {

  EnvironmentServiceEntity environmentServiceEntity;

  List<ComponentEntity> components;
}
