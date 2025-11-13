package com.dream11.odin.setup;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

import com.dream11.odin.Constants;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.extension.ExtensionContext;

@Slf4j
public class Setup extends AbstractTestSetup {

  public static void setupMockLogServer() {
    setupMockLogServer(Constants.TEST_TRACE_ID, HttpStatus.SC_OK);
  }

  public static void setupMockLogServer(String traceId, int httpStatus) {
    String sampleLogResponse =
        """
            {
              "hits": {
                "hits": [
                  {
                    "_index": "%s",
                    "_type": "_doc",
                    "_id": "-O3U-pUB9yrzJwanziRf",
                    "_score": 1,
                    "_source": {
                      "@timestamp": "2025-04-03T08:46:33.095Z",
                      "time": "2025-04-03T08:46:33.095549472Z",
                      "log": "Downloading application component",
                      "kubernetes": {
                        "pod_name": "msd-graphql-gateway-96235-job-6bn62",
                        "namespace_name": "uat-msd-graphql-gateway-10247-a01874f9",
                        "labels": {
                          "componentAction": "operate",
                          "componentActionId": "96235",
                          "componentName": "msd-graphql-gateway",
                          "envName": "uat",
                          "job-name": "msd-graphql-gateway-96235-job",
                          "serviceName": "msd-graphql-gateway"
                        },
                        "container_name": "runner"
                      },
                      "kubernetes_namespace": {
                        "name": "uat-msd-graphql-gateway-10247-a01874f9",
                        "labels": {
                          "createdAt": "1743669990601",
                          "createdBy": "odin",
                          "kubernetes.io/metadata.name": "uat-msd-graphql-gateway-10247-a01874f9",
                          "traceId": "%s"
                        }
                      }
                    },
                    "sort": [
                      1743500151783
                    ]
                  }
                ]
              }
            }
            """
            .formatted(traceId, traceId);

    String sampleIndexResponse =
        """
                [
                   {
                     "health": "green",
                     "status": "open",
                     "index": "%s",
                     "uuid": "ze_TcK48TI2oS81rE-UsaA",
                     "pri": "1",
                     "rep": "1",
                     "docs.count": "39699",
                     "docs.deleted": "0",
                     "store.size": "29.6mb",
                     "pri.store.size": "14.8mb"
                   }
               ]
            """
            .formatted(traceId);

    stubFor(
        get("/%s/_search".formatted(traceId))
            .willReturn(aResponse().withBody(sampleLogResponse).withStatus(httpStatus)));

    stubFor(
        get("/_cat/indices?format=json")
            .willReturn(aResponse().withBody(sampleIndexResponse).withStatus(httpStatus)));
  }

  @Override
  public void beforeAll(ExtensionContext extensionContext) throws IOException {
    if (!started) {
      this.setupInfrastructure();
      this.setupGrpcMock(extensionContext);
      this.extensionContext = extensionContext;
      this.setDefaultRxSchedulers(vertx);
      this.loadTestConfig();
      this.startMockOrchestratorVerticle();
      this.startApplication();
      started = true;
      extensionContext.getRoot().getStore(GLOBAL).put("test", this);
    }
  }
}
