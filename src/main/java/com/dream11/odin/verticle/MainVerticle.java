package com.dream11.odin.verticle;

import com.dream11.odin.Deployable;
import com.dream11.odin.config.AppConfig;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.injector.GuiceInjector;
import com.dream11.odin.util.ConfigUtil;
import com.dream11.odin.util.SharedDataUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.impl.cpu.CpuCoreSensor;
import io.vertx.reactivex.core.AbstractVerticle;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainVerticle extends AbstractVerticle {

  @Override
  public Completable rxStart() {
    List<Deployable> deployables =
        List.of(
            new Deployable(
                Constants.GRPC_VERTICLE,
                new DeploymentOptions().setInstances(this.getNumOfCores())),
            //            new Deployable(Constants.CONSUMER_VERTICLE, new
            // DeploymentOptions().setInstances(2)),
            new Deployable(
                Constants.REST_VERTICLE,
                new DeploymentOptions().setInstances(this.getNumOfCores())));

    return this.readConfig()
        .ignoreElement()
        .andThen(Observable.fromIterable(deployables))
        .flatMapSingle(
            deployable ->
                vertx.rxDeployVerticle(deployable.getVerticle(), deployable.getDeploymentOptions()))
        .toList()
        .ignoreElement()
        .doOnError(error -> log.error("Failed to deploy verticles", error))
        .doOnComplete(() -> log.info("Deployed all verticles!. Started Application"));
  }

  private Integer getNumOfCores() {
    return CpuCoreSensor.availableProcessors();
  }

  private Single<AppConfig> readConfig() {
    ObjectMapper objectMapper =
        SharedDataUtil.getInstance(this.vertx.getDelegate(), GuiceInjector.class)
            .getInstance(ObjectMapper.class);
    return ConfigUtil.getConfigRetriever(vertx)
        .rxGetConfig()
        .map(config -> objectMapper.readValue(config.encode(), AppConfig.class))
        .doOnSuccess(AppConfig::validate)
        .doOnSuccess(config -> SharedDataUtil.setInstance(this.vertx.getDelegate(), config));
  }
}
