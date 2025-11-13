package com.dream11.odin.entity;

import lombok.Builder;

@Builder
public record EnvironmentEntity(long id, long orgId, String name, String createdBy, int version) {
  public static final String COL_VERSION = "version";
  public static final String COL_STATUS = "status";
  public static final String COL_CREATED_BY = "created_by";

  private static final String STATUS_DELIMITER = "_";
}
