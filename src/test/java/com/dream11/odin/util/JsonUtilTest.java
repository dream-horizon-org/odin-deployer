package com.dream11.odin.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.dream11.odin.MainModule;
import com.dream11.odin.constant.EnvironmentStatus;
import com.dream11.odin.dto.v1.AccountInformation;
import com.dream11.odin.dto.v1.Environment;
import com.dream11.odin.injector.GuiceInjector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

@ExtendWith(VertxExtension.class)
class JsonUtilTest {

  @BeforeAll
  static void setup(Vertx vertx) {
    GuiceInjector injector =
        new GuiceInjector(Guice.createInjector(List.of(new MainModule(vertx))));
    SharedDataUtil.setInstance(vertx, injector);
  }

  @Test
  void testGetJsonObjectFromNestedJson() {
    // arrange
    JsonObject nestedObject = new JsonObject().put("key", "Hello");
    JsonObject jsonObject =
        new JsonObject().put("key", new JsonObject().put("nestedKey", nestedObject));

    // act
    JsonObject nestedJsonObject = JsonUtil.getJsonObjectFromNestedJson(jsonObject, "key.nestedKey");

    // assert
    assertThat(nestedJsonObject).isEqualTo(nestedObject);
  }

  @Test
  void testGetJsonObjectFromNestedJsonNonExistingKey() {
    // arrange
    JsonObject nestedObject = new JsonObject().put("key", "Hello");
    JsonObject jsonObject =
        new JsonObject().put("key", new JsonObject().put("nestedKey", nestedObject));

    // act
    JsonObject nestedJsonObject =
        JsonUtil.getJsonObjectFromNestedJson(jsonObject, "key.nonExisting");

    // assert
    assertThat(nestedJsonObject).isEqualTo(new JsonObject());
  }

  @Test
  void testJsonToProtoBuilder() {
    // arrange
    JsonObject jsonObject = new JsonObject().put("k1", "v1").put("k2", 1);

    // act
    Struct.Builder builder = JsonUtil.jsonToProtoBuilder(jsonObject, Struct.newBuilder());

    // assert
    assertThat(builder.getFieldsCount()).isEqualTo(2);
    assertThat(builder.getFieldsMap())
        .containsKey("k1")
        .containsEntry("k1", Value.newBuilder().setStringValue("v1").build())
        .containsKey("k2")
        .containsEntry("k2", Value.newBuilder().setNumberValue(1).build());
  }

  @Test
  void testJsonStringToProtoBuilder() {
    // arrange
    String jsonString = "{\"k1\":\"v1\",\"k2\":1,\"k3\":true,\"k4\":{\"nested\":\"value\"}}";

    // act
    Struct.Builder builder = JsonUtil.jsonStringToProtoBuilder(jsonString, Struct.newBuilder());

    // assert
    assertThat(builder.getFieldsCount()).isEqualTo(4);
    assertThat(builder.getFieldsMap())
        .containsKey("k1")
        .containsEntry("k1", Value.newBuilder().setStringValue("v1").build())
        .containsKey("k2")
        .containsEntry("k2", Value.newBuilder().setNumberValue(1).build())
        .containsKey("k3")
        .containsEntry("k3", Value.newBuilder().setBoolValue(true).build())
        .containsKey("k4");

    // verify nested object
    Struct nestedStruct = builder.getFieldsMap().get("k4").getStructValue();
    assertThat(nestedStruct.getFieldsMap())
        .containsKey("nested")
        .containsEntry("nested", Value.newBuilder().setStringValue("value").build());
  }

  @Test
  void testGetJsonFromProtoFiltered() {
    // Arrange
    Message message =
        Environment.newBuilder()
            .setName("test")
            .addAccountInformation(
                AccountInformation.newBuilder().setProviderAccountName("stag").build())
            .setStatus(EnvironmentStatus.RUNNING.name())
            .build();
    JsonObject result =
        new JsonObject()
            .put("name", "test")
            .put(
                "accountInformation",
                new JsonArray().add(new JsonObject().put("providerAccountName", "stag")));
    // Act
    JsonObject json = JsonUtil.getJsonFromProto(message, "name", "accountInformation");
    // Assert

    assertThat(json).isEqualTo(result);
  }

  @Test
  void testGetJsonFromProtoNotFiltered() {
    // Arrange
    Message message =
        Environment.newBuilder()
            .setName("test")
            .addAccountInformation(
                AccountInformation.newBuilder().setProviderAccountName("stag").build())
            .setStatus(EnvironmentStatus.RUNNING.name())
            .build();
    JsonObject result =
        new JsonObject()
            .put("name", "test")
            .put("status", EnvironmentStatus.RUNNING.name())
            .put(
                "accountInformation",
                new JsonArray().add(new JsonObject().put("providerAccountName", "stag")));
    // Act
    JsonObject json = JsonUtil.getJsonFromProto(message);
    // Assert

    assertThat(json).isEqualTo(result);
  }

  @Test
  void testGetJsonFromProtoFailure() throws InvalidProtocolBufferException {
    // Arrange
    Message message =
        Environment.newBuilder()
            .setName("test")
            .addAccountInformation(
                AccountInformation.newBuilder().setProviderAccountName("stag").build())
            .setStatus(EnvironmentStatus.RUNNING.name())
            .build();
    String errorMessage = "Invalid Message";

    try (MockedStatic<JsonFormat> jsonFormatMockedStatic = Mockito.mockStatic(JsonFormat.class)) {
      JsonFormat.Printer printer = Mockito.mock(JsonFormat.Printer.class);
      jsonFormatMockedStatic.when(JsonFormat::printer).thenReturn(printer);
      when(printer.print(message)).thenThrow(new InvalidProtocolBufferException(errorMessage));
      // Act & Assert
      assertThatThrownBy(() -> JsonUtil.getJsonFromProto(message))
          .isInstanceOf(InvalidProtocolBufferException.class)
          .hasMessage(errorMessage);
    }
  }

  @Test
  void shouldSortJsonCorrectlyWhenContainingArraysAndObjects(
      Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(
        v -> {
          try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonString =
                """
                    {"definitionConfig":{"components":[{"config":{"discovery":{"endpoint":"${ODIN_COMPONENT_NAME}-${ODIN_ENV_NAME}.${PRIVATE_HOSTED_ZONE}"},"version":"6.2.1"},"name":"guardian-redis","type":"redis","version":"1.0.1"},{"config":{"artifact":{"hooks":{"imageSetup":{"enabled":true,"script":".odin/setup.sh"},"postDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/post-deploy.sh"},"preDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/pre-deploy.sh"},"start":{"enabled":true,"script":".odin/start.sh"}},"name":"gatekeeper","version":"1.0.7"},"discovery":{"private":"${ODIN_COMPONENT_NAME}${TEAM_SUFFIX}.${PRIVATE_HOSTED_ZONE}"}},"depends_on":["guardian-redis","governor-aurora","guardian-rds"],"name":"gatekeeper","type":"application","version":"0.0.24"},{"config":{"artifact":{"hooks":{"imageSetup":{"enabled":true,"script":".odin/setup.sh"},"postDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/post-deploy.sh"},"preDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/pre-deploy.sh"},"start":{"enabled":true,"script":".odin/start.sh"}},"name":"cerberus","version":"1.1.4"},"discovery":{"private":"${ODIN_COMPONENT_NAME}${TEAM_SUFFIX}.${PRIVATE_HOSTED_ZONE}"}},"depends_on":["guardian-redis","governor-aurora","guardian-rds"],"name":"cerberus","type":"application","version":"0.0.24"},{"config":{"discovery":{"reader":"${ODIN_COMPONENT_NAME}-reader-${ODIN_ENV_NAME}.${PRIVATE_HOSTED_ZONE}","writer":"${ODIN_COMPONENT_NAME}-master-${ODIN_ENV_NAME}.${PRIVATE_HOSTED_ZONE}"},"masterPassword":"ENQmzHfQX4XR","masterUser":"d11stag","name":"${ODIN_COMPONENT_NAME}","version":"8.0.23"},"name":"guardian-rds","type":"optimus_rds","version":"1.2.4"},{"config":{"artifact":{"hooks":{"imageSetup":{"enabled":true,"script":".odin/setup.sh"},"postDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/post-deploy.sh"},"preDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/pre-deploy.sh"},"start":{"enabled":true,"script":".odin/start.sh"}},"name":"guardian-kong","version":"1.1.3"},"discovery":{"private":"guardian${TEAM_SUFFIX}.${PRIVATE_HOSTED_ZONE}","public":"guardian${TEAM_SUFFIX}.${PUBLIC_HOSTED_ZONE}"}},"depends_on":["guardian-redis","governor-aurora","guardian-rds"],"name":"guardian-kong","type":"application","version":"0.0.24"},{"config":{"discovery":{"reader":"${ODIN_COMPONENT_NAME}-reader-${ODIN_ENV_NAME}.${PRIVATE_HOSTED_ZONE}","writer":"${ODIN_COMPONENT_NAME}-master-${ODIN_ENV_NAME}.${PRIVATE_HOSTED_ZONE}"},"masterPassword":"ENQmzHfQX4XR","masterUser":"d11stag","name":"${ODIN_COMPONENT_NAME}","version":"8.0.23"},"name":"governor-aurora","type":"optimus_rds","version":"1.2.4"},{"config":{"artifact":{"hooks":{"imageSetup":{"enabled":true,"script":".odin/setup.sh"},"postDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/post-deploy.sh"},"preDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/pre-deploy.sh"},"start":{"enabled":true,"script":".odin/start.sh"}},"name":"governor","version":"0.20.1"},"discovery":{"private":"${ODIN_COMPONENT_NAME}${TEAM_SUFFIX}.${PRIVATE_HOSTED_ZONE}"}},"depends_on":["guardian-redis","governor-aurora","guardian-rds"],"name":"governor","type":"application","version":"0.0.24"}],"name":"guardian","team":"dreamauth","version":"1.1.5-SNAPSHOT"},"provisioningConfig":[{"component_name":"guardian-redis","deployment_type":"gcp_container","params":{}},{"component_name":"gatekeeper","deployment_type":"gcp_container","params":{"baseImage":{"repository":"debian-java","tag":"17"},"extraEnvVars":{"ENV_NAME":"${ENV_NAME}","HOOK_POST_DEPLOY_ENABLED":true,"HOOK_PRE_DEPLOY_ENABLED":true,"PRIVATE_HOSTED_ZONE":"${PRIVATE_HOSTED_ZONE}"},"healthCheckConfig":{"initialDelaySeconds":120.0,"livenessRoute":"/healthcheck","port":8080.0,"readinessRoute":"/healthcheck","type":"HTTP"},"ports":[{"name":"http","port":8080.0,"protocol":"TCP"}],"scale":{"autoscaling":{"enabled":false,"maxReplicas":100.0,"metrics":[],"minReplicas":1.0}}}},{"component_name":"cerberus","deployment_type":"gcp_container","params":{"baseImage":{"repository":"debian-java","tag":"17"},"extraEnvVars":{"ENV_NAME":"${ENV_NAME}","HOOK_POST_DEPLOY_ENABLED":true,"HOOK_PRE_DEPLOY_ENABLED":true,"PRIVATE_HOSTED_ZONE":"${PRIVATE_HOSTED_ZONE}"},"healthCheckConfig":{"initialDelaySeconds":120.0,"livenessRoute":"/healthcheck","port":8080.0,"readinessRoute":"/healthcheck","type":"HTTP"},"ports":[{"name":"http","port":8080.0,"protocol":"TCP"}],"scale":{"autoscaling":{"enabled":false,"maxReplicas":100.0,"metrics":[],"minReplicas":1.0}}}},{"component_name":"guardian-rds","deployment_type":"gcp_container","params":{"storageClass":"standard-rwo"}},{"component_name":"guardian-kong","deployment_type":"gcp_container","params":{"baseImage":{"repository":"debian-kong","tag":"2.8.1"},"extraEnvVars":{"COMPONENT_NAME":"guardian-kong","ENV_NAME":"${ENV_NAME}","HOOK_POST_DEPLOY_ENABLED":true,"HOOK_PRE_DEPLOY_ENABLED":true,"PRIVATE_HOSTED_ZONE":"${PRIVATE_HOSTED_ZONE}"},"healthCheckConfig":{"initialDelaySeconds":120.0,"livenessRoute":"healthcheck","port":8000.0,"readinessRoute":"healthcheck","type":"HTTP"},"ports":[{"name":"http","port":8000.0,"protocol":"TCP"}],"scale":{"autoscaling":{"enabled":false,"maxReplicas":100.0,"metrics":[],"minReplicas":1.0}}}},{"component_name":"governor-aurora","deployment_type":"gcp_container","params":{"storageClass":"standard-rwo"}},{"component_name":"governor","deployment_type":"gcp_container","params":{"baseImage":{"repository":"debian-java","tag":"17"},"extraEnvVars":{"ENV_NAME":"${ENV_NAME}","HOOK_POST_DEPLOY_ENABLED":true,"HOOK_PRE_DEPLOY_ENABLED":true,"PRIVATE_HOSTED_ZONE":"${PRIVATE_HOSTED_ZONE}"},"healthCheckConfig":{"initialDelaySeconds":120.0,"livenessRoute":"/healthcheck","port":8080.0,"readinessRoute":"/healthcheck","type":"HTTP"},"ports":[{"name":"http","port":8080.0,"protocol":"TCP"}],"scale":{"autoscaling":{"enabled":false,"maxReplicas":100.0,"metrics":[],"minReplicas":1.0}}}}]}
                    """;

            JsonNode jsonNode = mapper.readTree(jsonString);

            String expectedJsonString =
                """
                   {"definitionConfig":{"components":[{"config":{"artifact":{"hooks":{"imageSetup":{"enabled":true,"script":".odin/setup.sh"},"postDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/post-deploy.sh"},"preDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/pre-deploy.sh"},"start":{"enabled":true,"script":".odin/start.sh"}},"name":"gatekeeper","version":"1.0.7"},"discovery":{"private":"${ODIN_COMPONENT_NAME}${TEAM_SUFFIX}.${PRIVATE_HOSTED_ZONE}"}},"depends_on":["governor-aurora","guardian-rds","guardian-redis"],"name":"gatekeeper","type":"application","version":"0.0.24"},{"config":{"discovery":{"endpoint":"${ODIN_COMPONENT_NAME}-${ODIN_ENV_NAME}.${PRIVATE_HOSTED_ZONE}"},"version":"6.2.1"},"name":"guardian-redis","type":"redis","version":"1.0.1"},{"config":{"artifact":{"hooks":{"imageSetup":{"enabled":true,"script":".odin/setup.sh"},"postDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/post-deploy.sh"},"preDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/pre-deploy.sh"},"start":{"enabled":true,"script":".odin/start.sh"}},"name":"cerberus","version":"1.1.4"},"discovery":{"private":"${ODIN_COMPONENT_NAME}${TEAM_SUFFIX}.${PRIVATE_HOSTED_ZONE}"}},"depends_on":["governor-aurora","guardian-rds","guardian-redis"],"name":"cerberus","type":"application","version":"0.0.24"},{"config":{"artifact":{"hooks":{"imageSetup":{"enabled":true,"script":".odin/setup.sh"},"postDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/post-deploy.sh"},"preDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/pre-deploy.sh"},"start":{"enabled":true,"script":".odin/start.sh"}},"name":"guardian-kong","version":"1.1.3"},"discovery":{"private":"guardian${TEAM_SUFFIX}.${PRIVATE_HOSTED_ZONE}","public":"guardian${TEAM_SUFFIX}.${PUBLIC_HOSTED_ZONE}"}},"depends_on":["governor-aurora","guardian-rds","guardian-redis"],"name":"guardian-kong","type":"application","version":"0.0.24"},{"config":{"discovery":{"reader":"${ODIN_COMPONENT_NAME}-reader-${ODIN_ENV_NAME}.${PRIVATE_HOSTED_ZONE}","writer":"${ODIN_COMPONENT_NAME}-master-${ODIN_ENV_NAME}.${PRIVATE_HOSTED_ZONE}"},"masterPassword":"ENQmzHfQX4XR","masterUser":"d11stag","name":"${ODIN_COMPONENT_NAME}","version":"8.0.23"},"name":"guardian-rds","type":"optimus_rds","version":"1.2.4"},{"config":{"artifact":{"hooks":{"imageSetup":{"enabled":true,"script":".odin/setup.sh"},"postDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/post-deploy.sh"},"preDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/pre-deploy.sh"},"start":{"enabled":true,"script":".odin/start.sh"}},"name":"governor","version":"0.20.1"},"discovery":{"private":"${ODIN_COMPONENT_NAME}${TEAM_SUFFIX}.${PRIVATE_HOSTED_ZONE}"}},"depends_on":["governor-aurora","guardian-rds","guardian-redis"],"name":"governor","type":"application","version":"0.0.24"},{"config":{"discovery":{"reader":"${ODIN_COMPONENT_NAME}-reader-${ODIN_ENV_NAME}.${PRIVATE_HOSTED_ZONE}","writer":"${ODIN_COMPONENT_NAME}-master-${ODIN_ENV_NAME}.${PRIVATE_HOSTED_ZONE}"},"masterPassword":"ENQmzHfQX4XR","masterUser":"d11stag","name":"${ODIN_COMPONENT_NAME}","version":"8.0.23"},"name":"governor-aurora","type":"optimus_rds","version":"1.2.4"}],"name":"guardian","team":"dreamauth","version":"1.1.5-SNAPSHOT"},"provisioningConfig":[{"component_name":"gatekeeper","deployment_type":"gcp_container","params":{"baseImage":{"repository":"debian-java","tag":"17"},"extraEnvVars":{"ENV_NAME":"${ENV_NAME}","HOOK_POST_DEPLOY_ENABLED":true,"HOOK_PRE_DEPLOY_ENABLED":true,"PRIVATE_HOSTED_ZONE":"${PRIVATE_HOSTED_ZONE}"},"healthCheckConfig":{"initialDelaySeconds":120.0,"livenessRoute":"/healthcheck","port":8080.0,"readinessRoute":"/healthcheck","type":"HTTP"},"ports":[{"name":"http","port":8080.0,"protocol":"TCP"}],"scale":{"autoscaling":{"enabled":false,"maxReplicas":100.0,"metrics":[],"minReplicas":1.0}}}},{"component_name":"guardian-redis","deployment_type":"gcp_container","params":{}},{"component_name":"cerberus","deployment_type":"gcp_container","params":{"baseImage":{"repository":"debian-java","tag":"17"},"extraEnvVars":{"ENV_NAME":"${ENV_NAME}","HOOK_POST_DEPLOY_ENABLED":true,"HOOK_PRE_DEPLOY_ENABLED":true,"PRIVATE_HOSTED_ZONE":"${PRIVATE_HOSTED_ZONE}"},"healthCheckConfig":{"initialDelaySeconds":120.0,"livenessRoute":"/healthcheck","port":8080.0,"readinessRoute":"/healthcheck","type":"HTTP"},"ports":[{"name":"http","port":8080.0,"protocol":"TCP"}],"scale":{"autoscaling":{"enabled":false,"maxReplicas":100.0,"metrics":[],"minReplicas":1.0}}}},{"component_name":"guardian-kong","deployment_type":"gcp_container","params":{"baseImage":{"repository":"debian-kong","tag":"2.8.1"},"extraEnvVars":{"COMPONENT_NAME":"guardian-kong","ENV_NAME":"${ENV_NAME}","HOOK_POST_DEPLOY_ENABLED":true,"HOOK_PRE_DEPLOY_ENABLED":true,"PRIVATE_HOSTED_ZONE":"${PRIVATE_HOSTED_ZONE}"},"healthCheckConfig":{"initialDelaySeconds":120.0,"livenessRoute":"healthcheck","port":8000.0,"readinessRoute":"healthcheck","type":"HTTP"},"ports":[{"name":"http","port":8000.0,"protocol":"TCP"}],"scale":{"autoscaling":{"enabled":false,"maxReplicas":100.0,"metrics":[],"minReplicas":1.0}}}},{"component_name":"guardian-rds","deployment_type":"gcp_container","params":{"storageClass":"standard-rwo"}},{"component_name":"governor","deployment_type":"gcp_container","params":{"baseImage":{"repository":"debian-java","tag":"17"},"extraEnvVars":{"ENV_NAME":"${ENV_NAME}","HOOK_POST_DEPLOY_ENABLED":true,"HOOK_PRE_DEPLOY_ENABLED":true,"PRIVATE_HOSTED_ZONE":"${PRIVATE_HOSTED_ZONE}"},"healthCheckConfig":{"initialDelaySeconds":120.0,"livenessRoute":"/healthcheck","port":8080.0,"readinessRoute":"/healthcheck","type":"HTTP"},"ports":[{"name":"http","port":8080.0,"protocol":"TCP"}],"scale":{"autoscaling":{"enabled":false,"maxReplicas":100.0,"metrics":[],"minReplicas":1.0}}}},{"component_name":"governor-aurora","deployment_type":"gcp_container","params":{"storageClass":"standard-rwo"}}]}
                   """;

            JsonNode expectedJsonNode = mapper.readTree(expectedJsonString);
            jsonNode = JsonUtil.sortJsonNode(jsonNode);
            expectedJsonNode = JsonUtil.sortJsonNode(expectedJsonNode);

            assertEquals(expectedJsonNode, jsonNode, "Given Jsons do not match properly");
            testContext.completeNow();
          } catch (Throwable e) {
            testContext.failNow(e);
          }
        });
  }

  @Test
  void testSortJsonObject(Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(
        v -> {
          try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonString =
                """
                    {"definitionConfig":{"components":[{"config":{"discovery":{"endpoint":"${ODIN_COMPONENT_NAME}-${ODIN_ENV_NAME}.${PRIVATE_HOSTED_ZONE}"},"version":"6.2.1"},"name":"guardian-redis","type":"redis","version":"1.0.1"},{"config":{"artifact":{"hooks":{"imageSetup":{"enabled":true,"script":".odin/setup.sh"},"postDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/post-deploy.sh"},"preDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/pre-deploy.sh"},"start":{"enabled":true,"script":".odin/start.sh"}},"name":"gatekeeper","version":"1.0.7"},"discovery":{"private":"${ODIN_COMPONENT_NAME}${TEAM_SUFFIX}.${PRIVATE_HOSTED_ZONE}"}},"depends_on":["guardian-redis","governor-aurora","guardian-rds"],"name":"gatekeeper","type":"application","version":"0.0.24"},{"config":{"artifact":{"hooks":{"imageSetup":{"enabled":true,"script":".odin/setup.sh"},"postDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/post-deploy.sh"},"preDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/pre-deploy.sh"},"start":{"enabled":true,"script":".odin/start.sh"}},"name":"cerberus","version":"1.1.4"},"discovery":{"private":"${ODIN_COMPONENT_NAME}${TEAM_SUFFIX}.${PRIVATE_HOSTED_ZONE}"}},"depends_on":["guardian-redis","governor-aurora","guardian-rds"],"name":"cerberus","type":"application","version":"0.0.24"},{"config":{"discovery":{"reader":"${ODIN_COMPONENT_NAME}-reader-${ODIN_ENV_NAME}.${PRIVATE_HOSTED_ZONE}","writer":"${ODIN_COMPONENT_NAME}-master-${ODIN_ENV_NAME}.${PRIVATE_HOSTED_ZONE}"},"masterPassword":"ENQmzHfQX4XR","masterUser":"d11stag","name":"${ODIN_COMPONENT_NAME}","version":"8.0.23"},"name":"guardian-rds","type":"optimus_rds","version":"1.2.4"},{"config":{"artifact":{"hooks":{"imageSetup":{"enabled":true,"script":".odin/setup.sh"},"postDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/post-deploy.sh"},"preDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/pre-deploy.sh"},"start":{"enabled":true,"script":".odin/start.sh"}},"name":"guardian-kong","version":"1.1.3"},"discovery":{"private":"guardian${TEAM_SUFFIX}.${PRIVATE_HOSTED_ZONE}","public":"guardian${TEAM_SUFFIX}.${PUBLIC_HOSTED_ZONE}"}},"depends_on":["guardian-redis","governor-aurora","guardian-rds"],"name":"guardian-kong","type":"application","version":"0.0.24"},{"config":{"discovery":{"reader":"${ODIN_COMPONENT_NAME}-reader-${ODIN_ENV_NAME}.${PRIVATE_HOSTED_ZONE}","writer":"${ODIN_COMPONENT_NAME}-master-${ODIN_ENV_NAME}.${PRIVATE_HOSTED_ZONE}"},"masterPassword":"ENQmzHfQX4XR","masterUser":"d11stag","name":"${ODIN_COMPONENT_NAME}","version":"8.0.23"},"name":"governor-aurora","type":"optimus_rds","version":"1.2.4"},{"config":{"artifact":{"hooks":{"imageSetup":{"enabled":true,"script":".odin/setup.sh"},"postDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/post-deploy.sh"},"preDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/pre-deploy.sh"},"start":{"enabled":true,"script":".odin/start.sh"}},"name":"governor","version":"0.20.1"},"discovery":{"private":"${ODIN_COMPONENT_NAME}${TEAM_SUFFIX}.${PRIVATE_HOSTED_ZONE}"}},"depends_on":["guardian-redis","governor-aurora","guardian-rds"],"name":"governor","type":"application","version":"0.0.24"}],"name":"guardian","team":"dreamauth","version":"1.1.5-SNAPSHOT"},"provisioningConfig":[{"component_name":"guardian-redis","deployment_type":"gcp_container","params":{}},{"component_name":"gatekeeper","deployment_type":"gcp_container","params":{"baseImage":{"repository":"debian-java","tag":"17"},"extraEnvVars":{"ENV_NAME":"${ENV_NAME}","HOOK_POST_DEPLOY_ENABLED":true,"HOOK_PRE_DEPLOY_ENABLED":true,"PRIVATE_HOSTED_ZONE":"${PRIVATE_HOSTED_ZONE}"},"healthCheckConfig":{"initialDelaySeconds":120.0,"livenessRoute":"/healthcheck","port":8080.0,"readinessRoute":"/healthcheck","type":"HTTP"},"ports":[{"name":"http","port":8080.0,"protocol":"TCP"}],"scale":{"autoscaling":{"enabled":false,"maxReplicas":100.0,"metrics":[],"minReplicas":1.0}}}},{"component_name":"cerberus","deployment_type":"gcp_container","params":{"baseImage":{"repository":"debian-java","tag":"17"},"extraEnvVars":{"ENV_NAME":"${ENV_NAME}","HOOK_POST_DEPLOY_ENABLED":true,"HOOK_PRE_DEPLOY_ENABLED":true,"PRIVATE_HOSTED_ZONE":"${PRIVATE_HOSTED_ZONE}"},"healthCheckConfig":{"initialDelaySeconds":120.0,"livenessRoute":"/healthcheck","port":8080.0,"readinessRoute":"/healthcheck","type":"HTTP"},"ports":[{"name":"http","port":8080.0,"protocol":"TCP"}],"scale":{"autoscaling":{"enabled":false,"maxReplicas":100.0,"metrics":[],"minReplicas":1.0}}}},{"component_name":"guardian-rds","deployment_type":"gcp_container","params":{"storageClass":"standard-rwo"}},{"component_name":"guardian-kong","deployment_type":"gcp_container","params":{"baseImage":{"repository":"debian-kong","tag":"2.8.1"},"extraEnvVars":{"COMPONENT_NAME":"guardian-kong","ENV_NAME":"${ENV_NAME}","HOOK_POST_DEPLOY_ENABLED":true,"HOOK_PRE_DEPLOY_ENABLED":true,"PRIVATE_HOSTED_ZONE":"${PRIVATE_HOSTED_ZONE}"},"healthCheckConfig":{"initialDelaySeconds":120.0,"livenessRoute":"healthcheck","port":8000.0,"readinessRoute":"healthcheck","type":"HTTP"},"ports":[{"name":"http","port":8000.0,"protocol":"TCP"}],"scale":{"autoscaling":{"enabled":false,"maxReplicas":100.0,"metrics":[],"minReplicas":1.0}}}},{"component_name":"governor-aurora","deployment_type":"gcp_container","params":{"storageClass":"standard-rwo"}},{"component_name":"governor","deployment_type":"gcp_container","params":{"baseImage":{"repository":"debian-java","tag":"17"},"extraEnvVars":{"ENV_NAME":"${ENV_NAME}","HOOK_POST_DEPLOY_ENABLED":true,"HOOK_PRE_DEPLOY_ENABLED":true,"PRIVATE_HOSTED_ZONE":"${PRIVATE_HOSTED_ZONE}"},"healthCheckConfig":{"initialDelaySeconds":120.0,"livenessRoute":"/healthcheck","port":8080.0,"readinessRoute":"/healthcheck","type":"HTTP"},"ports":[{"name":"http","port":8080.0,"protocol":"TCP"}],"scale":{"autoscaling":{"enabled":false,"maxReplicas":100.0,"metrics":[],"minReplicas":1.0}}}}]}
                    """;
            JsonObject jsonObject = new JsonObject(jsonString);

            JsonObject sortedJsonObject = JsonUtil.sortJsonObject(jsonObject);

            String expectedJsonString =
                """
                    {"definitionConfig":{"components":[{"config":{"artifact":{"hooks":{"imageSetup":{"enabled":true,"script":".odin/setup.sh"},"postDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/post-deploy.sh"},"preDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/pre-deploy.sh"},"start":{"enabled":true,"script":".odin/start.sh"}},"name":"gatekeeper","version":"1.0.7"},"discovery":{"private":"${ODIN_COMPONENT_NAME}${TEAM_SUFFIX}.${PRIVATE_HOSTED_ZONE}"}},"depends_on":["governor-aurora","guardian-rds","guardian-redis"],"name":"gatekeeper","type":"application","version":"0.0.24"},{"config":{"discovery":{"endpoint":"${ODIN_COMPONENT_NAME}-${ODIN_ENV_NAME}.${PRIVATE_HOSTED_ZONE}"},"version":"6.2.1"},"name":"guardian-redis","type":"redis","version":"1.0.1"},{"config":{"artifact":{"hooks":{"imageSetup":{"enabled":true,"script":".odin/setup.sh"},"postDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/post-deploy.sh"},"preDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/pre-deploy.sh"},"start":{"enabled":true,"script":".odin/start.sh"}},"name":"cerberus","version":"1.1.4"},"discovery":{"private":"${ODIN_COMPONENT_NAME}${TEAM_SUFFIX}.${PRIVATE_HOSTED_ZONE}"}},"depends_on":["governor-aurora","guardian-rds","guardian-redis"],"name":"cerberus","type":"application","version":"0.0.24"},{"config":{"artifact":{"hooks":{"imageSetup":{"enabled":true,"script":".odin/setup.sh"},"postDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/post-deploy.sh"},"preDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/pre-deploy.sh"},"start":{"enabled":true,"script":".odin/start.sh"}},"name":"guardian-kong","version":"1.1.3"},"discovery":{"private":"guardian${TEAM_SUFFIX}.${PRIVATE_HOSTED_ZONE}","public":"guardian${TEAM_SUFFIX}.${PUBLIC_HOSTED_ZONE}"}},"depends_on":["governor-aurora","guardian-rds","guardian-redis"],"name":"guardian-kong","type":"application","version":"0.0.24"},{"config":{"discovery":{"reader":"${ODIN_COMPONENT_NAME}-reader-${ODIN_ENV_NAME}.${PRIVATE_HOSTED_ZONE}","writer":"${ODIN_COMPONENT_NAME}-master-${ODIN_ENV_NAME}.${PRIVATE_HOSTED_ZONE}"},"masterPassword":"ENQmzHfQX4XR","masterUser":"d11stag","name":"${ODIN_COMPONENT_NAME}","version":"8.0.23"},"name":"guardian-rds","type":"optimus_rds","version":"1.2.4"},{"config":{"artifact":{"hooks":{"imageSetup":{"enabled":true,"script":".odin/setup.sh"},"postDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/post-deploy.sh"},"preDeploy":{"dockerImage":"dreamsports.jfrog.io/d11-docker-artifacts/executor:1.0.3","enabled":true,"script":".odin/pre-deploy.sh"},"start":{"enabled":true,"script":".odin/start.sh"}},"name":"governor","version":"0.20.1"},"discovery":{"private":"${ODIN_COMPONENT_NAME}${TEAM_SUFFIX}.${PRIVATE_HOSTED_ZONE}"}},"depends_on":["governor-aurora","guardian-rds","guardian-redis"],"name":"governor","type":"application","version":"0.0.24"},{"config":{"discovery":{"reader":"${ODIN_COMPONENT_NAME}-reader-${ODIN_ENV_NAME}.${PRIVATE_HOSTED_ZONE}","writer":"${ODIN_COMPONENT_NAME}-master-${ODIN_ENV_NAME}.${PRIVATE_HOSTED_ZONE}"},"masterPassword":"ENQmzHfQX4XR","masterUser":"d11stag","name":"${ODIN_COMPONENT_NAME}","version":"8.0.23"},"name":"governor-aurora","type":"optimus_rds","version":"1.2.4"}],"name":"guardian","team":"dreamauth","version":"1.1.5-SNAPSHOT"},"provisioningConfig":[{"component_name":"gatekeeper","deployment_type":"gcp_container","params":{"baseImage":{"repository":"debian-java","tag":"17"},"extraEnvVars":{"ENV_NAME":"${ENV_NAME}","HOOK_POST_DEPLOY_ENABLED":true,"HOOK_PRE_DEPLOY_ENABLED":true,"PRIVATE_HOSTED_ZONE":"${PRIVATE_HOSTED_ZONE}"},"healthCheckConfig":{"initialDelaySeconds":120.0,"livenessRoute":"/healthcheck","port":8080.0,"readinessRoute":"/healthcheck","type":"HTTP"},"ports":[{"name":"http","port":8080.0,"protocol":"TCP"}],"scale":{"autoscaling":{"enabled":false,"maxReplicas":100.0,"metrics":[],"minReplicas":1.0}}}},{"component_name":"guardian-redis","deployment_type":"gcp_container","params":{}},{"component_name":"cerberus","deployment_type":"gcp_container","params":{"baseImage":{"repository":"debian-java","tag":"17"},"extraEnvVars":{"ENV_NAME":"${ENV_NAME}","HOOK_POST_DEPLOY_ENABLED":true,"HOOK_PRE_DEPLOY_ENABLED":true,"PRIVATE_HOSTED_ZONE":"${PRIVATE_HOSTED_ZONE}"},"healthCheckConfig":{"initialDelaySeconds":120.0,"livenessRoute":"/healthcheck","port":8080.0,"readinessRoute":"/healthcheck","type":"HTTP"},"ports":[{"name":"http","port":8080.0,"protocol":"TCP"}],"scale":{"autoscaling":{"enabled":false,"maxReplicas":100.0,"metrics":[],"minReplicas":1.0}}}},{"component_name":"guardian-kong","deployment_type":"gcp_container","params":{"baseImage":{"repository":"debian-kong","tag":"2.8.1"},"extraEnvVars":{"COMPONENT_NAME":"guardian-kong","ENV_NAME":"${ENV_NAME}","HOOK_POST_DEPLOY_ENABLED":true,"HOOK_PRE_DEPLOY_ENABLED":true,"PRIVATE_HOSTED_ZONE":"${PRIVATE_HOSTED_ZONE}"},"healthCheckConfig":{"initialDelaySeconds":120.0,"livenessRoute":"healthcheck","port":8000.0,"readinessRoute":"healthcheck","type":"HTTP"},"ports":[{"name":"http","port":8000.0,"protocol":"TCP"}],"scale":{"autoscaling":{"enabled":false,"maxReplicas":100.0,"metrics":[],"minReplicas":1.0}}}},{"component_name":"guardian-rds","deployment_type":"gcp_container","params":{"storageClass":"standard-rwo"}},{"component_name":"governor","deployment_type":"gcp_container","params":{"baseImage":{"repository":"debian-java","tag":"17"},"extraEnvVars":{"ENV_NAME":"${ENV_NAME}","HOOK_POST_DEPLOY_ENABLED":true,"HOOK_PRE_DEPLOY_ENABLED":true,"PRIVATE_HOSTED_ZONE":"${PRIVATE_HOSTED_ZONE}"},"healthCheckConfig":{"initialDelaySeconds":120.0,"livenessRoute":"/healthcheck","port":8080.0,"readinessRoute":"/healthcheck","type":"HTTP"},"ports":[{"name":"http","port":8080.0,"protocol":"TCP"}],"scale":{"autoscaling":{"enabled":false,"maxReplicas":100.0,"metrics":[],"minReplicas":1.0}}}},{"component_name":"governor-aurora","deployment_type":"gcp_container","params":{"storageClass":"standard-rwo"}}]}
                    """;
            JsonObject expectedJsonObject = new JsonObject(expectedJsonString);
            expectedJsonObject = JsonUtil.sortJsonObject(expectedJsonObject);

            assertEquals(
                mapper.readTree(expectedJsonObject.toString()),
                mapper.readTree(sortedJsonObject.toString()),
                "Given Jsons do not match properly");
            testContext.completeNow();
          } catch (Throwable e) {
            testContext.failNow(e);
          }
        });
  }
}
