package com.dream11.odin.verticle;

import com.dream11.grpc.AbstractGrpcVerticle;
import com.dream11.grpc.ClassInjector;
import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.client.MysqlClientFactory;
import com.dream11.odin.client.WebClient;
import com.dream11.odin.client.WebClientFactory;
import com.dream11.odin.client.oam.OdinAccountManagerClientFactory;
import com.dream11.odin.client.oam.OdinAccountManagerConfig;
import com.dream11.odin.config.AppConfig;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.grpc.provideraccount.v1.RxProviderAccountServiceGrpc;
import com.dream11.odin.injector.GuiceInjector;
import com.dream11.odin.interceptor.CustomContextServerInterceptor;
import com.dream11.odin.interceptor.CustomValidatingServerInterceptor;
import com.dream11.odin.util.ContextUtil;
import com.dream11.odin.util.SharedDataUtil;
import com.dream11.queue.producer.MessageProducer;
import com.dream11.queue.producer.MessageProducerFactory;
import io.grpc.ChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.util.AdvancedTlsX509TrustManager;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.reactivex.Completable;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.json.schema.SchemaRouterOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.json.schema.SchemaParser;
import io.vertx.reactivex.json.schema.SchemaRouter;
import java.security.cert.CertificateException;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GrpcVerticle extends AbstractGrpcVerticle {
  MysqlClient mysqlClient;
  ManagedChannel odinAccountManagerChannel;
  MessageProducer<String> producer;
  WebClient webClient;
  SchemaParser schemaParser;

  public GrpcVerticle() {
    super("com.dream11.odin", new HttpServerOptions().setPort(8080));
  }

  @Override
  public ClassInjector getInjector() {
    return SharedDataUtil.getInstance(this.vertx.getDelegate(), GuiceInjector.class);
  }

  @SneakyThrows
  @Override
  public Completable rxStart() {
    this.mysqlClient = MysqlClientFactory.getDefaultInstance(this.vertx);
    this.webClient = WebClientFactory.getDefaultInstance(this.vertx);
    AppConfig appConfig = this.getInjector().getInstance(AppConfig.class);
    this.producer = MessageProducerFactory.create(appConfig.getQueue().getRequest());
    this.odinAccountManagerChannel =
        this.getOdinAccountManagerChannel(appConfig.getOdinAccountManagerConfig());
    ContextUtil.setInstance(this.mysqlClient, Constants.MYSQL_CLIENT);
    ContextUtil.setInstance(this.webClient, Constants.WEB_CLIENT);
    ContextUtil.setInstance(this.producer, Constants.MESSAGE_PRODUCER);
    RxProviderAccountServiceGrpc.RxProviderAccountServiceStub providerAccountService =
        OdinAccountManagerClientFactory.getProviderAccountService(odinAccountManagerChannel);
    ContextUtil.setInstance(providerAccountService, Constants.PROVIDER_ACCOUNT_SERVICE);
    // TODO: Schema Parser is deprecated in favour of Schemarepository, replace it
    SchemaRouter schemaRouter =
        SchemaRouter.create(Vertx.currentContext().owner(), new SchemaRouterOptions());
    this.schemaParser = SchemaParser.createDraft7SchemaParser(schemaRouter);
    ContextUtil.setInstance(this.schemaParser, Constants.SCHEMA_PARSER);
    return this.mysqlClient
        .rxConnect(JsonObject.mapFrom(appConfig.getMysql()))
        .andThen(this.webClient.rxConnect(JsonObject.mapFrom(appConfig.getWebclient())))
        .andThen(super.rxStart());
  }

  @SneakyThrows
  @Override
  public Completable rxStop() {
    this.producer.close();
    return this.mysqlClient
        .rxClose()
        .doOnComplete(() -> odinAccountManagerChannel.shutdown())
        .doOnComplete(() -> log.info("Database connections closed successfully"))
        .doOnError(err -> log.info("Failed to close database connections", err))
        .doOnComplete(() -> this.webClient.close())
        .andThen(super.rxStop());
  }

  // Add context interceptor to the list of interceptors at first position
  @Override
  protected List<Class<?>> getGrpcInterceptors() {
    List<Class<?>> interceptors = super.getGrpcInterceptors();
    interceptors.add(0, CustomContextServerInterceptor.class);
    interceptors.add(1, CustomValidatingServerInterceptor.class);
    return interceptors;
  }

  private ManagedChannel getOdinAccountManagerChannel(
      OdinAccountManagerConfig odinAccountManagerConfig) throws CertificateException {
    NettyChannelBuilder channelBuilder;
    switch (odinAccountManagerConfig.getChannel()) {
      case PLAINTEXT:
        channelBuilder =
            NettyChannelBuilder.forAddress(
                odinAccountManagerConfig.getHost(), odinAccountManagerConfig.getPort());
        channelBuilder.usePlaintext();
        break;
      case TLS_INSECURE:
        ChannelCredentials insecureCredentials =
            TlsChannelCredentials.newBuilder()
                .trustManager(InsecureTrustManagerFactory.INSTANCE.getTrustManagers())
                .build();
        channelBuilder =
            NettyChannelBuilder.forAddress(
                odinAccountManagerConfig.getHost(),
                odinAccountManagerConfig.getPort(),
                insecureCredentials);
        break;
      case TLS_SECURE:
        ChannelCredentials secureCredentials =
            TlsChannelCredentials.newBuilder()
                .trustManager(
                    AdvancedTlsX509TrustManager.newBuilder()
                        .setVerification(
                            AdvancedTlsX509TrustManager.Verification
                                .CERTIFICATE_AND_HOST_NAME_VERIFICATION)
                        .build())
                .build();
        channelBuilder =
            NettyChannelBuilder.forAddress(
                odinAccountManagerConfig.getHost(),
                odinAccountManagerConfig.getPort(),
                secureCredentials);
        break;
      default:
        channelBuilder =
            NettyChannelBuilder.forAddress(
                odinAccountManagerConfig.getHost(), odinAccountManagerConfig.getPort());
    }
    return channelBuilder.build();
  }
}
