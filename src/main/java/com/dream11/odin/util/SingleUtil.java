package com.dream11.odin.util;

import io.reactivex.Single;
import io.reactivex.SingleTransformer;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.reactivex.SingleHelper;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import lombok.experimental.UtilityClass;
import lombok.val;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;
import org.slf4j.Logger;

@UtilityClass
public final class SingleUtil {

  /** Operator which adds debug logs to a Single. */
  public <T> SingleTransformer<T, T> applyDebugLogs(Logger log, String logPrefix) {
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
  public <T> SingleTransformer<T, T> applyDebugLogs(Logger log) {
    val logPrefix = Thread.currentThread().getStackTrace()[3].getMethodName();
    return applyDebugLogs(log, logPrefix);
  }

  /** Convert a vertx completable future to single */
  public static <T> Single<T> toSingle(Future<T> future) {
    return SingleHelper.toSingle(future::onComplete);
  }

  public static <T> Single<T> toSingle(CompletableFuture<T> completableFuture) {
    return toSingle(VertxCompletableFuture.from(Vertx.currentContext(), completableFuture));
  }

  public static <T> Single<T> fromCompletableFuture(CompletableFuture<T> completableFuture) {
    return Single.create(
        emitter ->
            completableFuture.whenComplete(
                (result, error) -> {
                  if (error != null) {
                    emitter.onError(error);
                  } else {
                    emitter.onSuccess(result);
                  }
                }));
  }

  public static <T> Single<T> toSingle(VertxCompletableFuture<T> vertxCompletableFuture) {
    return SingleHelper.toSingle(
        asyncResultHandler -> toFuture(vertxCompletableFuture).onComplete(asyncResultHandler));
  }

  public static <T> Future<T> toFuture(CompletableFuture<T> future) {
    return Future.future(
        promise ->
            future.whenComplete(
                (res, err) -> {
                  if (err != null) {
                    promise.fail(err);
                  } else {
                    promise.complete(res);
                  }
                }));
  }
}
