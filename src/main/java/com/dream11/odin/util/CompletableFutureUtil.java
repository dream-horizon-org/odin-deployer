package com.dream11.odin.util;

import io.reactivex.Completable;
import io.vertx.core.Future;
import io.vertx.reactivex.CompletableHelper;
import java.util.concurrent.CompletableFuture;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CompletableFutureUtil {

  public Completable toCompletable(CompletableFuture<Void> completableFuture) {
    return CompletableHelper.toCompletable(
        handler -> Future.fromCompletionStage(completableFuture).onComplete(handler));
  }
}
