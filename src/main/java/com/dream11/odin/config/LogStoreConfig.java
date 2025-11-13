package com.dream11.odin.config;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LogStoreConfig {
  public static final Boolean DEFAULT_ENABLE_SSL = false;

  String host;

  int port;

  String username;

  String password;

  Boolean enableSSL = DEFAULT_ENABLE_SSL;

  Integer pollingFrequencySeconds;

  Integer batchSize;

  Integer retryCount;

  Integer retryIntervalSeconds;
}
