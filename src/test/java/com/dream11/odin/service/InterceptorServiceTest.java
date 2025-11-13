package com.dream11.odin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dream11.odin.client.WebClient;
import com.dream11.odin.config.InterceptorConfig;
import com.dream11.odin.config.ProtobufMessageSerializer;
import com.dream11.odin.constant.Action;
import com.dream11.odin.dto.ComponentData;
import com.dream11.odin.dto.ComponentId;
import com.dream11.odin.dto.RequestMetaContext;
import com.dream11.odin.dto.UserDetails;
import com.dream11.odin.dto.v1.AccountInformation;
import com.dream11.odin.dto.v1.ComponentDefinition;
import com.dream11.odin.dto.v1.ComponentProvisioningConfig;
import com.dream11.odin.dto.v1.Environment;
import com.dream11.odin.injector.GuiceInjector;
import com.dream11.odin.util.SharedDataUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.protobuf.Message;
import com.google.protobuf.Struct;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.ext.web.client.HttpRequest;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InterceptorServiceTest {

  @Mock private WebClient webClient;
  @Mock private io.vertx.reactivex.ext.web.client.WebClient rxWebClient;
  @Mock private HttpRequest<io.vertx.reactivex.core.buffer.Buffer> httpRequest;
  @Mock private HttpResponse<io.vertx.reactivex.core.buffer.Buffer> httpResponse;
  @Mock private GuiceInjector guiceInjector;

  private MockedStatic<SharedDataUtil> sharedDataUtilMock;
  private InterceptorService interceptorService;
  private InterceptorConfig interceptorConfig;

  @BeforeEach
  void setUp() {
    // Setup default config
    interceptorConfig = new InterceptorConfig();
    interceptorConfig.setComponent(new ArrayList<>());

    // Setup HTTP config with defaults
    InterceptorConfig.InterceptorHttpConfig httpConfig =
        new InterceptorConfig.InterceptorHttpConfig();
    httpConfig.setTimeout(30);
    httpConfig.setRetryCount(2);
    interceptorConfig.setConfig(httpConfig);

    // Configure ObjectMapper with protobuf serializer
    ObjectMapper objectMapper = new ObjectMapper();
    SimpleModule module = new SimpleModule();
    module.addSerializer(Message.class, new ProtobufMessageSerializer());
    objectMapper.registerModule(module);

    // Mock SharedDataUtil to return the configured ObjectMapper instance
    sharedDataUtilMock = Mockito.mockStatic(SharedDataUtil.class);
    sharedDataUtilMock
        .when(() -> SharedDataUtil.getInstance(GuiceInjector.class))
        .thenReturn(guiceInjector);
    Mockito.lenient().when(guiceInjector.getInstance(ObjectMapper.class)).thenReturn(objectMapper);

    // Use the configured ObjectMapper instance for the service
    interceptorService = new InterceptorService(interceptorConfig, webClient, objectMapper);
  }

  @AfterEach
  void tearDown() {
    if (sharedDataUtilMock != null) {
      sharedDataUtilMock.close();
    }
  }

  @Test
  void shouldReturnOriginalDataWhenInterceptorListIsEmpty() {
    // Arrange
    ComponentData componentData = buildTestComponentData();
    RequestMetaContext requestMetaContext = buildTestComponentContext();
    Map<ComponentId, ComponentData> componentDataMap = new HashMap<>();
    ComponentId componentId =
        ComponentId.builder().componentName("test-component").action(Action.DEPLOY).build();
    componentDataMap.put(componentId, componentData);

    // Act
    Map<ComponentId, ComponentData> result =
        interceptorService.invokeInterceptors(componentDataMap, requestMetaContext).blockingGet();

    // Assert
    assertThat(result).isEqualTo(componentDataMap);
  }

  @Test
  void shouldInvokeSuccessfullyWhenSingleInterceptorIsConfigured() throws Exception {
    // Arrange
    List<String> interceptors = List.of("http://interceptor1.local");
    interceptorConfig.setComponent(interceptors);

    ComponentData componentData = buildTestComponentData();
    RequestMetaContext requestMetaContext = buildTestComponentContext();
    Map<ComponentId, ComponentData> componentDataMap = new HashMap<>();
    ComponentId componentId =
        ComponentId.builder().componentName("test-component").action(Action.DEPLOY).build();
    componentDataMap.put(componentId, componentData);

    // Mock HTTP response
    when(webClient.getWebClient()).thenReturn(rxWebClient);
    when(rxWebClient.postAbs(anyString())).thenReturn(httpRequest);
    when(httpRequest.expect(any())).thenReturn(httpRequest);
    when(httpRequest.timeout(any(Long.class))).thenReturn(httpRequest);
    when(httpRequest.rxSendBuffer(any())).thenReturn(Single.just(httpResponse));
    when(httpResponse.bodyAsString())
        .thenReturn(buildMockInterceptorResponse("modified-component").encode());

    // Act
    Map<ComponentId, ComponentData> result =
        interceptorService.invokeInterceptors(componentDataMap, requestMetaContext).blockingGet();

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.get(componentId).getComponentDefinition().getName())
        .isEqualTo("modified-component");
    verify(rxWebClient, times(1)).postAbs("http://interceptor1.local/v1/component/apply");
  }

  @Test
  void shouldChainInterceptorsSequentiallyWhenMultipleAreConfigured() throws Exception {
    // Arrange
    List<String> interceptors = List.of("http://interceptor1.local", "http://interceptor2.local");
    interceptorConfig.setComponent(interceptors);

    ComponentData componentData = buildTestComponentData();
    RequestMetaContext requestMetaContext = buildTestComponentContext();
    Map<ComponentId, ComponentData> componentDataMap = new HashMap<>();
    ComponentId componentId =
        ComponentId.builder().componentName("test-component").action(Action.DEPLOY).build();
    componentDataMap.put(componentId, componentData);

    // Mock HTTP responses
    JsonObject response1 = buildMockInterceptorResponse("modified-by-1");
    JsonObject response2 = buildMockInterceptorResponse("modified-by-2");

    when(webClient.getWebClient()).thenReturn(rxWebClient);
    when(rxWebClient.postAbs(anyString())).thenReturn(httpRequest);
    when(httpRequest.expect(any())).thenReturn(httpRequest);
    when(httpRequest.timeout(any(Long.class))).thenReturn(httpRequest);
    when(httpRequest.rxSendBuffer(any()))
        .thenReturn(Single.just(httpResponse), Single.just(httpResponse));

    when(httpResponse.bodyAsString()).thenReturn(response1.encode(), response2.encode());

    // Act
    Map<ComponentId, ComponentData> result =
        interceptorService.invokeInterceptors(componentDataMap, requestMetaContext).blockingGet();

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.get(componentId).getComponentDefinition().getName())
        .isEqualTo("modified-by-2");
    verify(rxWebClient, times(1)).postAbs("http://interceptor1.local/v1/component/apply");
    verify(rxWebClient, times(1)).postAbs("http://interceptor2.local/v1/component/apply");
  }

  @Test
  void shouldThrowExceptionWhenInterceptorReturnsNon200Status() throws Exception {
    // Arrange
    List<String> interceptors = List.of("http://interceptor1.local");
    interceptorConfig.setComponent(interceptors);

    ComponentData componentData = buildTestComponentData();
    RequestMetaContext requestMetaContext = buildTestComponentContext();
    Map<ComponentId, ComponentData> componentDataMap = new HashMap<>();
    ComponentId componentId =
        ComponentId.builder().componentName("test-component").action(Action.DEPLOY).build();
    componentDataMap.put(componentId, componentData);

    when(webClient.getWebClient()).thenReturn(rxWebClient);
    when(rxWebClient.postAbs(anyString())).thenReturn(httpRequest);
    when(httpRequest.expect(any())).thenReturn(httpRequest);
    when(httpRequest.timeout(any(Long.class))).thenReturn(httpRequest);
    when(httpRequest.rxSendBuffer(any()))
        .thenReturn(
            Single.error(new RuntimeException("HTTP 500")),
            Single.error(new RuntimeException("HTTP 500")),
            Single.error(new RuntimeException("HTTP 500")));

    // Act & Assert
    interceptorService
        .invokeInterceptors(componentDataMap, requestMetaContext)
        .test()
        .awaitDone(5, java.util.concurrent.TimeUnit.SECONDS)
        .assertError(throwable -> throwable.getMessage().contains("HTTP 500"));
  }

  @Test
  void shouldThrowExceptionWhenInterceptorTimesOut() throws Exception {
    // Arrange
    List<String> interceptors = List.of("http://slow-interceptor.local");
    interceptorConfig.setComponent(interceptors);

    ComponentData componentData = buildTestComponentData();
    RequestMetaContext requestMetaContext = buildTestComponentContext();
    Map<ComponentId, ComponentData> componentDataMap = new HashMap<>();
    ComponentId componentId =
        ComponentId.builder().componentName("test-component").action(Action.DEPLOY).build();
    componentDataMap.put(componentId, componentData);

    when(webClient.getWebClient()).thenReturn(rxWebClient);
    when(rxWebClient.postAbs(anyString())).thenReturn(httpRequest);
    when(httpRequest.expect(any())).thenReturn(httpRequest);
    when(httpRequest.timeout(any(Long.class))).thenReturn(httpRequest);
    // Ensure timeout error is returned consistently on all retry attempts
    when(httpRequest.rxSendBuffer(any()))
        .thenReturn(Single.error(new RuntimeException("Timeout")))
        .thenReturn(Single.error(new RuntimeException("Timeout")))
        .thenReturn(Single.error(new RuntimeException("Timeout")));

    // Act & Assert
    interceptorService
        .invokeInterceptors(componentDataMap, requestMetaContext)
        .test()
        .awaitDone(5, java.util.concurrent.TimeUnit.SECONDS)
        .assertError(throwable -> throwable.getMessage().contains("Timeout"));
  }

  @Test
  void shouldThrowExceptionWhenAdditionalContextIsNull() {
    // Arrange
    List<String> interceptors = List.of("http://interceptor1.local");
    interceptorConfig.setComponent(interceptors);

    ComponentData componentData = buildTestComponentData();
    RequestMetaContext requestMetaContext =
        RequestMetaContext.builder()
            .serviceName("test-service")
            .environment(Environment.newBuilder().setName("test-env").build())
            .userDetails(UserDetails.builder().userId("test-user").build())
            .additionalContext(null) // Null additional context
            .build();
    Map<ComponentId, ComponentData> componentDataMap = new HashMap<>();
    ComponentId componentId =
        ComponentId.builder().componentName("test-component").action(Action.DEPLOY).build();
    componentDataMap.put(componentId, componentData);

    // Act & Assert
    assertThatThrownBy(
            () ->
                interceptorService
                    .invokeInterceptors(componentDataMap, requestMetaContext)
                    .blockingGet())
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldThrowExceptionWhenActionIsMissingInContext() {
    // Arrange
    List<String> interceptors = List.of("http://interceptor1.local");
    interceptorConfig.setComponent(interceptors);

    ComponentData componentData = buildTestComponentData();
    RequestMetaContext requestMetaContext =
        RequestMetaContext.builder()
            .serviceName("test-service")
            .environment(Environment.newBuilder().setName("test-env").build())
            .userDetails(UserDetails.builder().userId("test-user").build())
            .additionalContext(Map.of("OTHER_KEY", "value")) // Missing ACTION
            .build();
    Map<ComponentId, ComponentData> componentDataMap = new HashMap<>();
    ComponentId componentId =
        ComponentId.builder().componentName("test-component").action(Action.DEPLOY).build();
    componentDataMap.put(componentId, componentData);

    // Act & Assert
    assertThatThrownBy(
            () ->
                interceptorService
                    .invokeInterceptors(componentDataMap, requestMetaContext)
                    .blockingGet())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Missing required ACTION");
  }

  // Helper methods
  private ComponentData buildTestComponentData() {
    return ComponentData.builder()
        .componentDefinition(
            ComponentDefinition.newBuilder().setName("test-component").setVersion("1.0.0").build())
        .componentProvisioningConfig(
            ComponentProvisioningConfig.newBuilder().setComponentName("test-component").build())
        .operationConfig(Struct.newBuilder().build())
        .environmentProviderAccounts(AccountInformation.newBuilder().build())
        .build();
  }

  private RequestMetaContext buildTestComponentContext() {
    return RequestMetaContext.builder()
        .serviceName("test-service")
        .environment(Environment.newBuilder().setName("test-env").build())
        .userDetails(UserDetails.builder().userId("test-user").build())
        .additionalContext(Map.of("action", "deploy"))
        .build();
  }

  private JsonObject buildMockInterceptorResponse(String componentName) {
    String jsonTemplate =
        """
                        {
                          "definition": {
                            "name": "%s",
                            "version": "1.0.0"
                          },
                          "provisioning": {
                            "component_name": "%s"
                          },
                          "operation": {},
                          "accountInformation": {},
                          "context": {
                            "environment": {
                              "name": "test-env"
                            },
                            "stage": {
                              "name": "deploy",
                              "config": {}
                            }
                          }
                        }
                        """;

    String json = String.format(jsonTemplate, componentName, componentName);
    return new JsonObject(json);
  }
}
