package com.dream11.odin;

import com.dream11.odin.injector.GuiceInjector;
import com.dream11.odin.util.SharedDataUtil;
import com.google.inject.Guice;
import io.reactivex.plugins.RxJavaPlugins;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Launcher;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.cpu.CpuCoreSensor;
import io.vertx.reactivex.RxHelper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainLauncher extends Launcher {

  public static void main(String[] args) {
    log.info("Starting Application..........");
    MainLauncher launcher = new MainLauncher();
    launcher.dispatch(args);
  }

  @Override
  public void beforeStartingVertx(VertxOptions vertxOptions) {
    vertxOptions.setEventLoopPoolSize(this.getNumOfCores()).setPreferNativeTransport(true);
  }

  @Override
  public void afterStartingVertx(Vertx vertx) {
    log.info("Initializing guice modules");
    this.initializeGuiceInjector(vertx);
    this.setDefaultRxSchedulers(vertx);
  }

  @Override
  public void beforeDeployingVerticle(DeploymentOptions deploymentOptions) {
    log.info("Deploying verticles..........");
    deploymentOptions.setInstances(1);
  }

  @Override
  public void handleDeployFailed(
      Vertx vertx, String verticle, DeploymentOptions deploymentOptions, Throwable cause) {
    log.error(
        "Deployment of {} verticle failed with options {} due to error: {}",
        verticle,
        deploymentOptions,
        cause.getMessage(),
        cause);
    super.handleDeployFailed(vertx, verticle, deploymentOptions, cause);
  }

  private Integer getNumOfCores() {
    return CpuCoreSensor.availableProcessors();
  }

  private void initializeGuiceInjector(Vertx vertx) {
    GuiceInjector injector =
        new GuiceInjector(Guice.createInjector(List.of(new MainModule(vertx))));
    SharedDataUtil.setInstance(vertx, injector);
  }

  private void setDefaultRxSchedulers(Vertx vertx) {
    RxJavaPlugins.setComputationSchedulerHandler(s -> RxHelper.scheduler(vertx));
    RxJavaPlugins.setIoSchedulerHandler(s -> RxHelper.blockingScheduler(vertx));
    RxJavaPlugins.setNewThreadSchedulerHandler(s -> RxHelper.scheduler(vertx));
  }
}
