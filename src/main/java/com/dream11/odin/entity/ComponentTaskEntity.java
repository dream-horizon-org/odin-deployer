package com.dream11.odin.entity;

import com.dream11.odin.constant.Action;
import com.fasterxml.jackson.annotation.JsonAlias;
import io.vertx.core.json.JsonObject;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class ComponentTaskEntity extends TaskEntity {
  @JsonAlias({"serviceTaskEntity", "service_task_entity"})
  ServiceTaskEntity serviceTaskEntity;

  @JsonAlias({"componentName", "component_name"})
  String componentName;

  JsonObject response;

  Action action;

  JsonObject accounts;

  @JsonAlias({"configHash", "config_hash"})
  String configHash;

  public ComponentTaskEntity withId(Long id) {
    return ComponentTaskEntity.builder()
        .id(id)
        .config(this.getConfig())
        .configHash(this.getConfigHash())
        .status(this.getStatus())
        .version(this.getVersion())
        .createdBy(this.getCreatedBy())
        .updatedBy(this.getUpdatedBy())
        .serviceTaskEntity(this.serviceTaskEntity)
        .componentName(this.componentName)
        .action(this.action)
        .response(this.response)
        .accounts(this.accounts)
        .build();
  }

  public ComponentTaskEntity withServiceTaskEntity(ServiceTaskEntity serviceTaskEntity) {
    return ComponentTaskEntity.builder()
        .config(this.getConfig())
        .configHash(this.getConfigHash())
        .status(this.getStatus())
        .version(this.getVersion())
        .createdBy(this.getCreatedBy())
        .updatedBy(this.getUpdatedBy())
        .serviceTaskEntity(serviceTaskEntity)
        .componentName(this.componentName)
        .action(this.action)
        .response(this.response)
        .accounts(this.accounts)
        .build();
  }
}
