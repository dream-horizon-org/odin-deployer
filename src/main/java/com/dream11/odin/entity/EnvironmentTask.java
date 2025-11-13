package com.dream11.odin.entity;

import com.dream11.odin.constant.TaskStatus;
import java.util.Optional;
import lombok.Builder;

@Builder
public record EnvironmentTask(
    long id,
    long envId,
    TaskStatus status,
    long actionId,
    int version,
    String createdBy,
    String serviceAccountSnapshot,
    String providerAccountName,
    Optional<String> response,
    Optional<String> traceId) {
  public static final String COL_VERSION = "version";
  public static final String COL_STATUS = "status";
  public static final String COL_CREATED_BY = "created_by";
  public static final String COL_ACTION_ID = "action_id";
  public static final String COL_ENVID = "env_id";
  public static final String COL_ID = "id";
  public static final String COL_RESPONSE = "response";
  public static final String COL_SERVICE_ACCOUNTS_SNAPSHOT = "service_accounts_snapshot";
  public static final String COL_PROVIDER_ACCOUNT_NAME = "provider_account_name";
  public static final String COL_TRACE_ID = "trace_id";
}
