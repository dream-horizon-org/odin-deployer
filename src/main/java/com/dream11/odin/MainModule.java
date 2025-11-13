package com.dream11.odin;

import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.client.WebClient;
import com.dream11.odin.config.AppConfig;
import com.dream11.odin.config.InterceptorConfig;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.grpc.provideraccount.v1.RxProviderAccountServiceGrpc;
import com.dream11.odin.util.ContextUtil;
import com.dream11.odin.util.SharedDataUtil;
import com.dream11.queue.consumer.MessageConsumer;
import com.dream11.queue.producer.MessageProducer;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import io.vertx.core.Vertx;
import io.vertx.reactivex.json.schema.SchemaParser;

public class MainModule extends AbstractModule {
  protected final Vertx vertx;
  private final ObjectMapper objectMapper =
      JsonMapper.builder()
          .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
          .serializationInclusion(JsonInclude.Include.NON_NULL)
          .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
          .build();

  public MainModule(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  protected void configure() {
    bind(ObjectMapper.class).toInstance(this.objectMapper);
    bind(Vertx.class).toInstance(this.vertx);
    bind(io.vertx.reactivex.core.Vertx.class)
        .toInstance(io.vertx.reactivex.core.Vertx.newInstance(this.vertx));
    bind(MysqlClient.class).toProvider(() -> ContextUtil.getInstance(Constants.MYSQL_CLIENT));
    bind(MessageConsumer.class)
        .toProvider(() -> ContextUtil.getInstance(Constants.MESSAGE_CONSUMER));
    bind(WebClient.class).toProvider(() -> ContextUtil.getInstance(Constants.WEB_CLIENT));
    bind(AppConfig.class).toProvider(() -> SharedDataUtil.getInstance(AppConfig.class));
    bind(InterceptorConfig.class)
        .toProvider(() -> SharedDataUtil.getInstance(AppConfig.class).getInterceptors());
    bind(RxProviderAccountServiceGrpc.RxProviderAccountServiceStub.class)
        .toProvider(() -> ContextUtil.getInstance(Constants.PROVIDER_ACCOUNT_SERVICE));
    bind(SchemaParser.class).toProvider(() -> ContextUtil.getInstance(Constants.SCHEMA_PARSER));
    bind(new TypeLiteral<MessageProducer<String>>() {})
        .toProvider(() -> ContextUtil.getInstance(Constants.MESSAGE_PRODUCER));
  }
}
