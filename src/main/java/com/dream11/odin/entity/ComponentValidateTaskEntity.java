package com.dream11.odin.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.vertx.core.json.JsonObject;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
public class ComponentValidateTaskEntity extends TaskEntity {
  @JsonAlias({"componentName", "component_name"})
  String componentName;

  JsonObject response;

  JsonObject accounts;

  @JsonAlias({"serviceValidateTaskEntity", "service_validate_task_entity"})
  ServiceValidateTaskEntity serviceValidateTaskEntity;

  @JsonAlias({"configHash", "config_hash"})
  String configHash;

  public ComponentValidateTaskEntity withId(Long id) {
    return ComponentValidateTaskEntity.builder()
        .id(id)
        .componentName(this.componentName)
        .serviceValidateTaskEntity(this.serviceValidateTaskEntity)
        .accounts(this.accounts)
        .config(this.getConfig())
        .configHash(this.getConfigHash())
        .status(this.getStatus())
        .version(this.getVersion())
        .createdBy(this.getCreatedBy())
        .updatedBy(this.getUpdatedBy())
        .response(this.response)
        .build();
  }
}
