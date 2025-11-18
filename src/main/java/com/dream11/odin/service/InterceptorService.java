package com.dream11.odin.service;

import com.dream11.odin.client.WebClient;
import com.dream11.odin.config.InterceptorConfig;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.dto.ComponentData;
import com.dream11.odin.dto.ComponentIdentifier;
import com.dream11.odin.dto.RequestMetaContext;
import com.dream11.odin.dto.interceptor.EnvironmentContext;
import com.dream11.odin.dto.interceptor.InterceptorContext;
import com.dream11.odin.dto.interceptor.StageContext;
import com.dream11.odin.dto.v1.AccountInformation;
import com.dream11.odin.dto.v1.ComponentDefinition;
import com.dream11.odin.dto.v1.ComponentProvisioningConfig;
import com.dream11.odin.util.JsonUtil;
import com.dream11.odin.util.RxJavaUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.protobuf.Struct;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.predicate.ResponsePredicate;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class InterceptorService {

  private static final String ENDPOINT = "/v1/component/apply";
  private static final String OPERATION = "operation";

  final InterceptorConfig interceptors;
  final WebClient webClient;
  final ObjectMapper objectMapper;

  public Single<Map<ComponentIdentifier, ComponentData>> invokeInterceptors(
      Map<ComponentIdentifier, ComponentData> componentDataMap,
      RequestMetaContext requestMetaContext) {

    // If no interceptors configured, return original data
    if (interceptors.getComponent().isEmpty()) {
      log.debug("No interceptors configured, skipping interceptor invocation");
      return Single.just(componentDataMap);
    }

    List<Single<AbstractMap.SimpleEntry<ComponentIdentifier, ComponentData>>> singles =
        componentDataMap.entrySet().stream()
            .map(
                entry ->
                    invokeInterceptorsForComponent(entry.getValue(), requestMetaContext)
                        .map(
                            updatedComponentData ->
                                new AbstractMap.SimpleEntry<>(
                                    entry.getKey(), updatedComponentData)))
            .toList();

    return Single.merge(singles)
        .collect(HashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()));
  }

  private Single<ComponentData> invokeInterceptorsForComponent(
      ComponentData componentData, RequestMetaContext requestMetaContext) {
    String initialRequest = buildInterceptorPayloadAsJsonString(componentData, requestMetaContext);

    // Chain interceptors sequentially
    Single<String> result = Single.just(initialRequest);

    for (String interceptorUrl : interceptors.getComponent()) {
      result =
          result.flatMap(
              previousResponse -> callComponentInterceptor(interceptorUrl, previousResponse));
    }

    // Convert final response back to ComponentData
    return result.map(this::convertPayloadToComponentData);
  }

  @SneakyThrows
  private String buildInterceptorPayloadAsJsonString(
      ComponentData componentData, RequestMetaContext requestMetaContext) {
    Map<String, Object> additionalContext = requestMetaContext.getAdditionalContext();
    Objects.requireNonNull(additionalContext);

    if (!additionalContext.containsKey(Constants.ACTION)) {
      throw new IllegalStateException("Missing required ACTION in component context");
    }
    String stageName = additionalContext.get(Constants.ACTION).toString();
    JsonNode operationJson =
        componentData.getOperationConfig() != null
            ? JsonUtil.convertProtoToJsonNode(componentData.getOperationConfig())
            : null;

    ObjectNode baseNode = objectMapper.valueToTree(componentData);
    if (operationJson != null) {
      baseNode.set(OPERATION, operationJson);
    }

    baseNode.set(
        "interceptorContext",
        objectMapper.valueToTree(
            InterceptorContext.builder()
                .environment(
                    EnvironmentContext.builder()
                        .name(
                            requestMetaContext.getEnvironment() != null
                                ? requestMetaContext.getEnvironment().getName()
                                : null)
                        .build())
                .stage(StageContext.builder().name(stageName).config(additionalContext).build())
                .build()));

    return objectMapper.writeValueAsString(baseNode);
  }

  private Single<String> callComponentInterceptor(String interceptorUrl, String payload) {

    log.info("Calling interceptor at URL: {}", interceptorUrl);

    // Configuration setup
    int maxRetries = interceptors.getConfig().getRetryCount();

    return makeHttpRequest(interceptorUrl, payload)
        .retryWhen(RxJavaUtil.retryWithDelay(1, TimeUnit.SECONDS, maxRetries))
        .doOnSuccess(response -> log.info("Successfully called interceptor at {}", interceptorUrl))
        .doOnError(
            error ->
                log.error(
                    "Error calling interceptor at {} because of {}. All retries exhausted.",
                    interceptorUrl,
                    error.getMessage(),
                    error));
  }

  private Single<String> makeHttpRequest(String interceptorUrl, String jsonPayload) {
    long timeoutMillis = interceptors.getConfig().getTimeout() * 1000L;

    return webClient
        .getWebClient()
        .postAbs(interceptorUrl + ENDPOINT)
        .expect(ResponsePredicate.SC_SUCCESS)
        .expect(ResponsePredicate.JSON)
        .timeout(timeoutMillis)
        .rxSendBuffer(Buffer.buffer(jsonPayload))
        .map(HttpResponse::bodyAsString);
  }

  private ComponentData convertPayloadToComponentData(String interceptorPayloadJson) {
    JsonObject interceptorPayload = new JsonObject(interceptorPayloadJson);
    Struct operationStruct;
    if (!interceptorPayload.containsKey(OPERATION)) {
      log.warn(
          "Operation config is null or missing for component: {}, using empty Struct",
          interceptorPayload.getJsonObject("definition").getString("name"));
      operationStruct = Struct.newBuilder().build();
    } else {
      operationStruct =
          JsonUtil.jsonStringToProtoBuilder(
                  interceptorPayload.getString(OPERATION), Struct.newBuilder())
              .build();
    }

    ComponentDefinition componentDefinition =
        JsonUtil.jsonToProtoBuilder(
                interceptorPayload.getJsonObject("definition"), ComponentDefinition.newBuilder())
            .build();
    ComponentProvisioningConfig provisioning =
        JsonUtil.jsonToProtoBuilder(
                interceptorPayload.getJsonObject("provisioning"),
                ComponentProvisioningConfig.newBuilder())
            .build();
    AccountInformation accountInformation =
        JsonUtil.jsonToProtoBuilder(
                interceptorPayload.getJsonObject("accountInformation"),
                AccountInformation.newBuilder())
            .build();

    return ComponentData.builder()
        .componentDefinition(componentDefinition)
        .componentProvisioningConfig(provisioning)
        .operationConfig(operationStruct)
        .environmentProviderAccounts(accountInformation)
        .build();
  }
}
