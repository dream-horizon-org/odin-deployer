package com.dream11.odin.verticle;

import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.client.MysqlClientFactory;
import com.dream11.odin.client.WebClient;
import com.dream11.odin.client.WebClientFactory;
import com.dream11.odin.client.oam.OdinAccountManagerClientFactory;
import com.dream11.odin.config.AppConfig;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.grpc.provideraccount.v1.RxProviderAccountServiceGrpc;
import com.dream11.odin.injector.GuiceInjector;
import com.dream11.odin.rest.filter.AuthFilter;
import com.dream11.odin.util.ContextUtil;
import com.dream11.odin.util.SharedDataUtil;
import com.dream11.queue.producer.MessageProducer;
import com.dream11.queue.producer.MessageProducerFactory;
import com.dream11.rest.AbstractRestVerticle;
import com.dream11.rest.ClassInjector;
import hu.akarnokd.rxjava3.bridge.RxJavaBridge;
import io.grpc.ChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.json.schema.SchemaRouterOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.json.schema.SchemaParser;
import io.vertx.reactivex.json.schema.SchemaRouter;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RestVerticle extends AbstractRestVerticle {
  MysqlClient mysqlClient;
  ManagedChannel odinAccountManagerChannel;
  MessageProducer<String> producer;
  WebClient webClient;
  SchemaParser schemaParser;

  public RestVerticle() {
    super("com.dream11.odin.rest", new HttpServerOptions().setPort(9000));
  }

  @Override
  protected ClassInjector getInjector() {
    return SharedDataUtil.getInstance(this.vertx.getDelegate(), GuiceInjector.class);
  }

  @SneakyThrows
  @Override
  public Completable rxStart() {
    this.mysqlClient =
        MysqlClientFactory.getDefaultInstance(
            io.vertx.reactivex.core.Vertx.newInstance(this.vertx.getDelegate()));
    this.webClient =
        WebClientFactory.getDefaultInstance(
            io.vertx.reactivex.core.Vertx.newInstance(this.vertx.getDelegate()));
    AppConfig appConfig = this.getInjector().getInstance(AppConfig.class);
    this.producer = MessageProducerFactory.create(appConfig.getQueue().getRequest());
    ChannelCredentials credentials =
        TlsChannelCredentials.newBuilder()
            .trustManager(InsecureTrustManagerFactory.INSTANCE.getTrustManagers())
            .build();
    this.odinAccountManagerChannel =
        NettyChannelBuilder.forAddress(
                appConfig.getOdinAccountManagerConfig().getHost(),
                appConfig.getOdinAccountManagerConfig().getPort(),
                credentials)
            .build();
    ContextUtil.setInstance(this.webClient, Constants.WEB_CLIENT);
    ContextUtil.setInstance(this.producer, Constants.MESSAGE_PRODUCER);
    ContextUtil.setInstance(this.mysqlClient, Constants.MYSQL_CLIENT);

    RxProviderAccountServiceGrpc.RxProviderAccountServiceStub providerAccountService =
        OdinAccountManagerClientFactory.getProviderAccountService(odinAccountManagerChannel);
    ContextUtil.setInstance(providerAccountService, Constants.PROVIDER_ACCOUNT_SERVICE);

    // TODO: Schema Parser is deprecated in favour of Schemarepository, replace it
    SchemaRouter schemaRouter =
        SchemaRouter.create(Vertx.currentContext().owner(), new SchemaRouterOptions());
    this.schemaParser = SchemaParser.createDraft7SchemaParser(schemaRouter);
    ContextUtil.setInstance(this.schemaParser, Constants.SCHEMA_PARSER);

    return RxJavaBridge.toV3Completable(
            this.mysqlClient.rxConnect(JsonObject.mapFrom(appConfig.getMysql())))
        .andThen(
            RxJavaBridge.toV3Completable(
                this.webClient.rxConnect(JsonObject.mapFrom(appConfig.getWebclient()))))
        .andThen(super.rxStart());
  }

  @Override
  protected List<Object> getProviderObjects() {
    List<Object> providerObjects = super.getProviderObjects();
    providerObjects.add(this.getInjector().getInstance(AuthFilter.class));
    return providerObjects;
  }

  @SneakyThrows
  @Override
  public Completable rxStop() {
    Completable mysql = RxJavaBridge.toV3Completable(this.mysqlClient.rxClose());
    return mysql
        .doOnComplete(() -> odinAccountManagerChannel.shutdown())
        .doOnComplete(() -> log.info("Database connections closed successfully"))
        .doOnError(err -> log.info("Failed to close database connections", err))
        .andThen(super.rxStop());
  }
}
