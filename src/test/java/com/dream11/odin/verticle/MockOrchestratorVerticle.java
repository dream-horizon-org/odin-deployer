package com.dream11.odin.verticle;

import com.dream11.odin.Constants;
import com.dream11.odin.contract.request.RequestMessage;
import com.dream11.odin.contract.response.ResponseMessage;
import com.dream11.odin.util.SingleUtil;
import com.dream11.odin.util.TestUtil;
import com.dream11.queue.impl.sqs.SqsConfig;
import com.dream11.queue.impl.sqs.SqsConsumer;
import com.dream11.queue.impl.sqs.SqsProducer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;

@Slf4j
public class MockOrchestratorVerticle extends AbstractVerticle {

  static SqsAsyncClient sqsClient;

  static SqsConsumer messageConsumer;

  static SqsProducer<String> messageProducer;

  static CompletableFuture<Void> future;

  @Override
  public Completable rxStart() {
    return Completable.fromAction(
        () -> {
          sqsClient =
              SqsAsyncClient.builder()
                  .endpointOverride(
                      URI.create(System.getProperty(Constants.SQS_REQUEST_QUEUE_ENDPOINT)))
                  .region(Region.of(System.getProperty(Constants.SQS_REQUEST_QUEUE_REGION)))
                  .build();

          SqsConfig sqsRequestConfig = TestUtil.getSqsRequestConfig();
          SqsConfig sqsResponseConfig = TestUtil.getSqsResponseConfig();
          messageConsumer = new SqsConsumer(sqsRequestConfig, sqsClient);
          messageProducer = new SqsProducer<>(sqsResponseConfig, sqsClient, __ -> __);
          log.info("MockOrchestratorVerticle started");
          this.vertx.setPeriodic(
              Constants.POLLING_FREQUENCY_MILLISECOND, timerId -> pollForMessages());
        });
  }

  @SneakyThrows
  private void pollForMessages() {
    SingleUtil.toSingle(messageConsumer.receive())
        .flattenAsObservable(message -> message)
        // Mock of processing time
        .filter(message -> message.body() != null && !message.body().isEmpty())
        .observeOn(Schedulers.io())
        .map(
            message -> {
              messageConsumer.acknowledgeMessage(message).get();
              return handleMessage(message, message::body);
            })
        .switchIfEmpty(Observable.just(false) /* Ignore empty messages */)
        .subscribe();
  }

  private <T> boolean handleMessage(Message receivedMessage, Supplier<String> messageSupplier)
      throws ExecutionException, InterruptedException {
    if (receivedMessage != null) {
      String decompressedMessage = TestUtil.decodeAndDecompress(messageSupplier.get());
      // Process message according to type
      log.info("Received message: {}", decompressedMessage);
      ObjectMapper objectMapper = new ObjectMapper();
      try {
        RequestMessage requestMessage =
            objectMapper.readValue(decompressedMessage, RequestMessage.class);
        switch (requestMessage.getType()) {
          case NAMESPACE:
            log.debug("Processing namespace message: {}", requestMessage);
            messageConsumer.acknowledgeMessage(receivedMessage).get();
            ResponseMessage namespaceResponseMessage =
                TestUtil.getMockOrchestratorNamespaceResponses(requestMessage);
            // TODO: not asserting on request body tags for namespace response message because the
            // values are test specific
            //            TestUtil.assertNamespaceRequestBodyTags(receivedMessage);
            JsonObject namespaceResponseMessageJson =
                new JsonObject(objectMapper.writeValueAsString(namespaceResponseMessage));
            messageProducer.send(namespaceResponseMessageJson.toString());
            break;
          case SERVICE:
            log.debug("Processing service message: {}", requestMessage);
            messageConsumer.acknowledgeMessage(receivedMessage).get();
            List<ResponseMessage> responseMessages =
                TestUtil.getMockOrchestratorServiceResponses(requestMessage);
            responseMessages.forEach(
                responseMessage -> {
                  try {
                    JsonObject responseMessageJson =
                        new JsonObject(objectMapper.writeValueAsString(responseMessage));
                    future = messageProducer.send(responseMessageJson.toString());
                    synchronized (future) {
                      // Add wait between messages so that response messages reflect partial
                      // success/failure state
                      future.wait(1000);
                    }
                  } catch (JsonProcessingException e) {
                    throw new IllegalArgumentException(
                        "Error while parsing service response message");
                  } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                  }
                });
            break;
          default:
            throw new IllegalArgumentException("Invalid message type");
        }
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException("Error while parsing request message");
      }
    }
    return false;
  }

  @Override
  public void stop() {
    messageConsumer.close();
    messageProducer.close();
  }
}
