package com.dream11.odin.entity;

import com.dream11.odin.constant.TaskStatus;
import lombok.Builder;

@Builder
public record EnvironmentAccount(
    long id,
    long environmentId,
    TaskStatus status,
    String action,
    String createdBy,
    String accountData,
    String accountName) {
  public static final String COL_VERSION = "version";
  public static final String COL_STATUS = "status";
  public static final String COL_CREATED_BY = "created_by";
  public static final String COL_ACTION_ID = "action_id";
  public static final String COL_ENVIRONMENT_ID = "environment_id";
  public static final String COL_ID = "id";
  public static final String COL_SERVICE_ACCOUNTS_SNAPSHOT = "account_data";
  public static final String COL_PROVIDER_ACCOUNT_NAME = "account_name";
  public static final String COL_ACCOUNT_DATA = "account_data";
  public static final String COL_ACTION = "action";
}
