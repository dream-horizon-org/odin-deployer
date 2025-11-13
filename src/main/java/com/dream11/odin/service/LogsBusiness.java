package com.dream11.odin.service;

import static com.dream11.odin.util.LogUtil.addDebugLogLevelIfNoLevelPresent;
import static com.dream11.odin.util.LogUtil.getLogLevel;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.ApplicationContext;
import com.dream11.odin.client.WebClient;
import com.dream11.odin.config.AppConfig;
import com.dream11.odin.config.LogStoreConfig;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.error.OdinError;
import com.dream11.odin.grpc.logs.GetLogsRequest;
import com.dream11.odin.grpc.logs.GetLogsResponse;
import com.dream11.odin.grpc.logs.Log;
import com.dream11.odin.util.ApplicationUtil;
import com.google.inject.Inject;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.HttpRequest;
import io.vertx.reactivex.ext.web.client.predicate.ResponsePredicate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class LogsBusiness {

  final AppConfig appConfig;
  final WebClient webClient;
  final HashMap<String, String> componentFontColour = new HashMap<>();

  public Flowable<GetLogsResponse> getLogs(GetLogsRequest request) {
    if (request.getSearchAfterParamsCount() > 0) {
      ApplicationContext.setSearchAfterParam(new ArrayList<>(request.getSearchAfterParamsList()));
    }
    return request.getFollow() ? streamLogs(request) : fetchLogs(request);
  }

  Flowable<GetLogsResponse> fetchLogs(GetLogsRequest request) {
    return fetchIndex(request.getTraceId())
        .flatMapPublisher(index -> readFromES(index).toFlowable());
  }

  Flowable<GetLogsResponse> streamLogs(GetLogsRequest request) {
    final AtomicReference<Integer> retryCount = new AtomicReference<>(30);
    return fetchIndex(request.getTraceId())
        .flatMapPublisher(
            index ->
                Flowable.interval(
                        0,
                        appConfig.getLogStoreConfig().getPollingFrequencySeconds(),
                        TimeUnit.SECONDS)
                    .flatMap(
                        tick ->
                            readFromES(index, ApplicationContext.getSearchAfterParam())
                                .toFlowable()))
        .takeUntil(response -> isRetryable(response, retryCount) == Boolean.TRUE);
  }

  Single<String> fetchIndex(String traceId) {
    return getLogStoreRequest("/_cat/indices?format=json")
        .expect(ResponsePredicate.SC_SUCCESS)
        .rxSend()
        .map(
            response -> {
              JsonArray indices = new JsonArray(response.bodyAsString());
              return indices.stream()
                  .map(JsonObject.class::cast)
                  .map(index -> index.getString("index"))
                  .filter(indexName -> indexName.startsWith(traceId))
                  .findFirst()
                  .orElseThrow(() -> ExceptionUtil.getException(OdinError.LOGS_NOT_FOUND, traceId));
            });
  }

  Single<GetLogsResponse> readFromES(String index) {
    return readFromES(index, new ArrayList<>());
  }

  Single<GetLogsResponse> readFromES(String index, List<Long> searchAfterParams) {
    JsonObject params =
        new JsonObject()
            .put("size", appConfig.getLogStoreConfig().getBatchSize())
            .put(
                "sort",
                new JsonArray()
                    .add(new JsonObject().put("ingest_time", "asc"))
                    .add(new JsonObject().put("@timestamp", "asc")));
    if (searchAfterParams.size() > 1) {
      JsonArray searchAfterParamsJson = new JsonArray();
      searchAfterParamsJson.add(searchAfterParams.get(0));
      searchAfterParamsJson.add(searchAfterParams.get(1));
      params.put("search_after", searchAfterParamsJson);
    }
    log.debug(String.format("Fetching logs with params: %s", params.encodePrettily()));
    return getLogStoreRequest(String.format("/%s/%s", index, "_search"))
        .expect(ResponsePredicate.SC_SUCCESS)
        .rxSendJsonObject(params)
        .map(bufferHttpResponse -> getLogsResponse(bufferHttpResponse.bodyAsJsonObject()));
  }

  private GetLogsResponse getLogsResponse(JsonObject response) {
    GetLogsResponse.Builder result = GetLogsResponse.newBuilder();
    JsonArray logHits = response.getJsonObject("hits").getJsonArray("hits");
    logHits.forEach(
        node -> {
          JsonObject nodeJson = (JsonObject) node;
          String logLine = nodeJson.getJsonObject("_source").getString("log", "");
          List<Long> currentSearchAfterParam = nodeJson.getJsonArray("sort").getList();
          ApplicationContext.setSearchAfterParam(currentSearchAfterParam);
          String componentName =
              nodeJson
                  .getJsonObject("_source")
                  .getJsonObject("kubernetes")
                  .getJsonObject("labels")
                  .getString("componentName", "");

          String cleanedLog = addDebugLogLevelIfNoLevelPresent(logLine);
          result.addLogs(
              Log.newBuilder()
                  .setMessage(
                      String.format(
                          "[ %s%-20s%s ] %s",
                          componentFontColour.computeIfAbsent(
                              componentName,
                              name -> ApplicationUtil.getANSIColorForComponent(componentName)),
                          componentName,
                          Constants.ANSI_DEFAULT,
                          cleanedLog))
                  .setTimestamp(nodeJson.getJsonArray("sort").getLong(0))
                  .addAllSearchAfterParams(currentSearchAfterParam)
                  .setLevel(getLogLevel(cleanedLog))
                  .build());
        });
    return result.build();
  }

  private boolean isRetryable(GetLogsResponse response, AtomicReference<Integer> retryCount) {
    if (response.getLogsCount() == 0) {
      if (retryCount.get() > 0) {
        retryCount.getAndUpdate(count -> count - 1);
        return false;
      }
      return true;
    } else {
      retryCount.set(appConfig.getLogStoreConfig().getRetryCount());
      return false;
    }
  }

  private HttpRequest<Buffer> getLogStoreRequest(String address) {
    LogStoreConfig config = appConfig.getLogStoreConfig();
    return webClient
        .getWebClient()
        .get(config.getPort(), config.getHost(), address)
        .basicAuthentication(config.getUsername(), config.getPassword())
        .ssl(config.getEnableSSL())
        .putHeader("Content-Type", "application/json");
  }
}
