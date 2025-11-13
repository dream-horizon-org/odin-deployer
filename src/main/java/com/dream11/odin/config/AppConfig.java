package com.dream11.odin.config;

import com.dream11.odin.auth.AuthConfig;
import com.dream11.odin.client.oam.OdinAccountManagerConfig;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AppConfig {
  static final Integer DEFAULT_SERVICE_DB_STATUS_CHECK_INTERVAL_MS = 5;

  @NotNull Map<String, Object> mysql = new HashMap<>();

  @NotNull Map<String, Object> webclient = new HashMap<>();

  @NotNull @Valid QueueConfig queue = new QueueConfig();

  @Valid @NotNull AuthConfig authConfig;

  @Valid @NotNull OdinAccountManagerConfig odinAccountManagerConfig;

  @NotNull Integer envDbStatusCheckIntervalSecs;

  @NotNull Integer serviceDbStatusCheckIntervalSecs = DEFAULT_SERVICE_DB_STATUS_CHECK_INTERVAL_MS;

  @Valid @NotNull LogStoreConfig logStoreConfig;

  @Valid @NotNull InterceptorConfig interceptors;

  public void validate() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();
      Set<ConstraintViolation<AppConfig>> constraintViolations = validator.validate(this);
      if (!constraintViolations.isEmpty()) {
        throw new ConstraintViolationException(constraintViolations);
      }
    }
  }
}
