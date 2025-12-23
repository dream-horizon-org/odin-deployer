package com.dream11.odin.entity;

import lombok.Builder;

@Builder
public record EnvironmentEntity(long id, long orgId, String name, String createdBy) {
  public EnvironmentEntity withId(long id) {
    return new EnvironmentEntity(id, orgId, name, createdBy);
  }
}
