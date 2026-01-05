package com.dream11.odin.config;

import com.dream11.odin.auth.AuthConfig;
import com.dream11.odin.client.oam.OdinAccountManagerConfig;
import com.dream11.odin.util.ApplicationUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AppConfig {
  static final Integer DEFAULT_SERVICE_DB_STATUS_CHECK_INTERVAL_MS = 5;
  static final Integer DEFAULT_ENV_DB_STATUS_CHECK_INTERVAL_MS = 5;
  @NotNull Map<String, Object> mysql = new HashMap<>();

  @NotNull Map<String, Object> webclient = new HashMap<>();

  @NotNull @Valid QueueConfig queue = new QueueConfig();

  @Valid @NotNull AuthConfig authConfig;

  @Valid @NotNull OdinAccountManagerConfig odinAccountManagerConfig;

  @NotNull Integer envDbStatusCheckIntervalSecs = DEFAULT_ENV_DB_STATUS_CHECK_INTERVAL_MS;

  @NotNull Integer serviceDbStatusCheckIntervalSecs = DEFAULT_SERVICE_DB_STATUS_CHECK_INTERVAL_MS;

  @Valid @NotNull LogStoreConfig logStoreConfig;

  @Valid @NotNull InterceptorConfig interceptors;

  public void validate() {
    ApplicationUtil.validate(this);
  }
}
