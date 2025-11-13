package com.dream11.odin.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class InterceptorConfig {
  @NotNull List<String> component = new ArrayList<>();

  @Valid @NotNull InterceptorHttpConfig config = new InterceptorHttpConfig();

  @Data
  @NoArgsConstructor
  public static class InterceptorHttpConfig {
    @NotNull Integer timeout;

    @NotNull Integer retryCount;
  }
}
