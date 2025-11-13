package com.dream11.odin.util;

import io.reactivex.Flowable;
import io.reactivex.MaybeTransformer;
import io.reactivex.functions.Function;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.slf4j.Logger;

@UtilityClass
public final class RxJavaUtil {

  /** Operator which adds debug logs to a Single. */
  public <T> MaybeTransformer<T, T> applyDebugLogs(Logger log, String logPrefix) {
    AtomicLong startTime = new AtomicLong();
    return single ->
        single
            .doOnSubscribe(
                disposable -> {
                  startTime.set(System.currentTimeMillis());
                  log.debug("{} Subscribed", logPrefix);
                })
            .doOnSuccess(
                result -> {
                  long elapsedTime = System.currentTimeMillis() - startTime.get();
                  log.debug("{} Received after {}ms {}", logPrefix, elapsedTime, result);
                })
            .doOnError(
                err -> {
                  long elapsedTime = System.currentTimeMillis() - startTime.get();
                  log.error(
                      "{} Error after {}ms {}", logPrefix, elapsedTime, err.getMessage(), err);
                });
  }

  /** Operator which adds debug logs to a Single. */
  public <T> MaybeTransformer<T, T> applyDebugLogs(Logger log) {
    val logPrefix = Thread.currentThread().getStackTrace()[3].getMethodName();
    return applyDebugLogs(log, logPrefix);
  }

  public Function<? super Flowable<Throwable>, Flowable<?>> retryWithDelay(
      int delay, TimeUnit delayTimeUnit, int maxAttempts) {
    AtomicInteger retryCount = new AtomicInteger();
    return errors ->
        errors.flatMap(
            err -> {
              if (retryCount.getAndIncrement() < maxAttempts) {
                return Flowable.timer(delay, delayTimeUnit);
              }
              return Flowable.error(err);
            });
  }
}
