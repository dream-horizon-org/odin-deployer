package com.dream11.odin.setup;

import com.dream11.odin.Constants;
import java.io.IOException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * Separate test context for Interceptor integration tests. This allows testing interceptor
 * functionality without affecting other integration tests.
 */
@Slf4j
public class InterceptorSetup extends AbstractTestSetup {

  @Getter private static GenericContainer<?> echoContainer;

  @Override
  public void beforeAll(ExtensionContext extensionContext) throws IOException {
    if (!started) {
      // Start echo container for interceptor testing
      startEchoContainer();

      log.info(
          "Started echo container on port: {}, will configure as interceptor endpoint",
          echoContainer.getMappedPort(5050));

      // Setup infrastructure
      this.setupInfrastructure();
      this.setupGrpcMock(extensionContext);
      this.extensionContext = extensionContext;
      this.setDefaultRxSchedulers(vertx);
      this.loadTestConfig();
      this.startMockOrchestratorVerticle();
      this.startApplication();
      started = true;
    }
  }

  @Override
  protected void closeAdditionalResources() {
    log.info("Closing interceptor-specific resources");
    if (echoContainer != null && echoContainer.isRunning()) {
      echoContainer.stop();
      log.info("Stopped echo container");
    }
    removeInterceptorProperties();
  }

  private void startEchoContainer() {
    // Build custom echo server image from Dockerfile
    echoContainer =
        new GenericContainer<>(
                new ImageFromDockerfile()
                    .withFileFromClasspath("Dockerfile", "echo-server/Dockerfile")
                    .withFileFromClasspath("echo_server.py", "echo-server/echo_server.py"))
            .withExposedPorts(5050);

    echoContainer.start();

    log.info("Echo server container started on port: {}", echoContainer.getMappedPort(5050));
  }

  @Override
  protected void setupAdditionalSystemProperties() {
    System.setProperty(Constants.INTERCEPTORS_CONFIG_TIMEOUT, "2");
    System.setProperty(Constants.INTERCEPTORS_CONFIG_RETRY_COUNT, "3");
    System.setProperty(
        Constants.INTERCEPTORS_CONFIG_COMPONENT,
        "[\"http://localhost:" + echoContainer.getMappedPort(5050) + "\"]");
  }

  private void removeInterceptorProperties() {
    log.info("Resetting interceptor properties");
    System.setProperty(Constants.INTERCEPTORS_CONFIG_COMPONENT, "[]");
  }
}
