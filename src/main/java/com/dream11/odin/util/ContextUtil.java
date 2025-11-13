package com.dream11.odin.util;

import io.vertx.reactivex.core.Vertx;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public final class ContextUtil {
  private static final String CONTEXT_INSTANCE_PREFIX = "__vertx.contextUtils.";
  private static final String CONTEXT_CLASS_PREFIX = "__class.";

  /** Shared across verticle instance. */
  public <T> T getInstance(String key) {
    return Vertx.currentContext().get(CONTEXT_INSTANCE_PREFIX + CONTEXT_CLASS_PREFIX + key);
  }

  /**
   * Accessible from anywhere in this verticle instance. Note: This has to be set from one of the
   * VertxThreads (may cause NullPointerException otherwise) We are intentionally avoiding
   * vertx.getOrCreateContext() to ensure better coding practices
   */
  public <T> void setInstance(T object, String key) {
    Vertx.currentContext().put(CONTEXT_INSTANCE_PREFIX + CONTEXT_CLASS_PREFIX + key, object);
  }
}
