package com.dream11.odin.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
public class ServiceValidateTaskEntity extends TaskEntity {
  String name;

  @JsonAlias({"serviceVersion", "service_version"})
  String serviceVersion;

  @JsonAlias({"serviceConfigHash", "service_config_hash"})
  String serviceConfigHash;

  @JsonAlias({"traceId", "trace_id"})
  String traceId;

  public ServiceValidateTaskEntity withId(Long id) {
    return ServiceValidateTaskEntity.builder()
        .id(id)
        .config(this.getConfig())
        .serviceConfigHash(this.getServiceConfigHash())
        .status(this.getStatus())
        .version(this.getVersion())
        .createdBy(this.getCreatedBy())
        .updatedBy(this.getUpdatedBy())
        .name(this.name)
        .serviceVersion(this.serviceVersion)
        .build();
  }
}
