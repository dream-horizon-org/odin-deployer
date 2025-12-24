package com.dream11.odin.entity;

import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record EnvironmentEntity(
    long id,
    long orgId,
    String name,
    String status,
    String createdBy,
    String updatedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {
  public EnvironmentEntity withId(long id) {
    return new EnvironmentEntity(
        id, orgId, name, status, createdBy, updatedBy, createdAt, updatedAt);
  }
}
