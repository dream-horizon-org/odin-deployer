package com.dream11.odin.verticle;

import static com.dream11.odin.constant.Constants.RECEIVE_WAIT_TIMEOUT_SECONDS;

import com.dream11.grpc.ClassInjector;
import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.client.MysqlClientFactory;
import com.dream11.odin.client.WebClient;
import com.dream11.odin.client.WebClientFactory;
import com.dream11.odin.config.AppConfig;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.dto.response.ResponseMessage;
import com.dream11.odin.dto.response.ResponseMessageType;
import com.dream11.odin.injector.GuiceInjector;
import com.dream11.odin.service.responseprocessor.NamespaceResponseProcessor;
import com.dream11.odin.service.responseprocessor.ResponseProcessor;
import com.dream11.odin.service.responseprocessor.ServiceResponseProcessor;
import com.dream11.odin.util.ContextUtil;
import com.dream11.odin.util.SharedDataUtil;
import com.dream11.odin.util.SingleUtil;
import com.dream11.queue.Message;
import com.dream11.queue.consumer.MessageConsumer;
import com.dream11.queue.consumer.MessageConsumerFactory;
import com.dream11.queue.impl.sqs.SqsConsumer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.Completable;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConsumerVerticle extends AbstractVerticle {

  ClassInjector classInjector;
  MessageConsumer messageConsumer;
  MysqlClient mysqlClient;
  WebClient webClient;

  @Override
  public Completable rxStart() {
    AppConfig appConfig = SharedDataUtil.getInstance(AppConfig.class);
    this.mysqlClient = MysqlClientFactory.getDefaultInstance(this.vertx);
    this.webClient = WebClientFactory.getDefaultInstance(this.vertx);
    ContextUtil.setInstance(this.mysqlClient, Constants.MYSQL_CLIENT);
    ContextUtil.setInstance(this.webClient, Constants.WEB_CLIENT);
    // todo: find the optimal executor
    this.messageConsumer = MessageConsumerFactory.create(appConfig.getQueue().getResponse());
    ContextUtil.setInstance(this.messageConsumer, Constants.MESSAGE_CONSUMER);
    this.classInjector = SharedDataUtil.getInstance(vertx.getDelegate(), GuiceInjector.class);
    this.pollForMessages();
    return this.mysqlClient
        .rxConnect(JsonObject.mapFrom(appConfig.getMysql()))
        .andThen(this.webClient.rxConnect(JsonObject.mapFrom(appConfig.getWebclient())));
  }

  @SneakyThrows
  private void pollForMessages() {
    if (this.messageConsumer instanceof SqsConsumer) {
      SingleUtil.toSingle(this.messageConsumer.receive(RECEIVE_WAIT_TIMEOUT_SECONDS))
          .flattenAsObservable(message -> message)
          .filter(message -> message.getBody() != null && !message.getBody().isEmpty())
          .flatMapSingle(this::processMessage)
          .doFinally(this::pollForMessages)
          .subscribe(
              message -> log.debug("Message processed successfully: {}", message),
              err -> log.error("Error while processing message:", err));
    }
  }

  private Single<ResponseMessage> processMessage(Message message) throws JsonProcessingException {
    log.info("Message received: {}", message.getBody());
    ResponseMessage responseMessage =
        this.classInjector
            .getInstance(ObjectMapper.class)
            .readValue(message.getBody(), ResponseMessage.class);
    return this.getProcessor(responseMessage.getType())
        .process(responseMessage)
        .andThen(
            Single.defer(
                    () -> SingleUtil.toSingle(this.messageConsumer.acknowledgeMessage(message)))
                .map(ack -> responseMessage));
  }

  @Override
  public Completable rxStop() {
    this.messageConsumer.close();
    return this.mysqlClient
        .rxClose()
        .doOnComplete(() -> log.info("Database connections closed successfully"))
        .doOnError(err -> log.info("Failed to close database connections", err))
        .doOnComplete(() -> this.webClient.close());
  }

  private ResponseProcessor getProcessor(ResponseMessageType messageType) {
    return switch (messageType) {
      case NAMESPACE -> this.classInjector.getInstance(NamespaceResponseProcessor.class);
      case SERVICE_STATUS, COMPONENT_STATUS -> this.classInjector.getInstance(
          ServiceResponseProcessor.class);
    };
  }
}
