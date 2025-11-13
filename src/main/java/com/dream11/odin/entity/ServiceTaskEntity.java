package com.dream11.odin.entity;

import com.dream11.odin.constant.Action;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
public class ServiceTaskEntity extends TaskEntity {
  Action actions;

  @JsonAlias({"envId", "env_id"})
  Long envId;

  String name;

  @JsonAlias({"serviceVersion", "service_version"})
  String serviceVersion;

  @JsonAlias({"serviceConfigHash", "service_config_hash"})
  String serviceConfigHash;

  @JsonAlias({"traceId", "trace_id"})
  String traceId;

  public ServiceTaskEntity withId(Long id) {
    return ServiceTaskEntity.builder()
        .id(id)
        .config(this.getConfig())
        .serviceConfigHash(this.getServiceConfigHash())
        .status(this.getStatus())
        .version(this.getVersion())
        .createdBy(this.getCreatedBy())
        .updatedBy(this.getUpdatedBy())
        .actions(this.actions)
        .envId(this.envId)
        .name(this.name)
        .serviceVersion(this.serviceVersion)
        .build();
  }
}
