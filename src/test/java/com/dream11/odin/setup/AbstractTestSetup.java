package com.dream11.odin.setup;

import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

import com.dream11.odin.Constants;
import com.dream11.odin.client.oam.OdinAccountManagerClientFactory;
import com.dream11.odin.config.AppConfig;
import com.dream11.odin.grpc.provideraccount.v1.RxProviderAccountServiceGrpc;
import com.dream11.odin.injector.GuiceInjector;
import com.dream11.odin.oam.MockOAMProviderAccountService;
import com.dream11.odin.util.SharedDataUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import io.reactivex.plugins.RxJavaPlugins;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.RxHelper;
import io.vertx.reactivex.core.Vertx;
import java.io.IOException;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Rule;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;

@Slf4j
public abstract class AbstractTestSetup
    implements BeforeAllCallback, AfterAllCallback, ExtensionContext.Store.CloseableResource {

  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  protected static boolean started = false;
  protected final Vertx vertx = Vertx.vertx();
  protected MySQLContainer<?> mySQLContainer;
  protected LocalStackContainer localStackContainer;
  protected ExtensionContext extensionContext;

  @Override
  public void afterAll(ExtensionContext extensionContext) {
    // Explicitly close resources to release ports before next test class
    if (started) {
      close();
      started = false;
    }
  }

  @Override
  public void close() {
    log.info("Closing all resources");
    closeAdditionalResources();
    // Close vertx synchronously to ensure all verticles are undeployed and ports released
    if (this.vertx != null) {
      this.vertx.rxClose().blockingAwait();
      log.info("Closed Vertx instance and released all ports");
    }
    if (this.mySQLContainer != null) {
      this.mySQLContainer.close();
    }
    if (this.localStackContainer != null) {
      this.localStackContainer.close();
    }
  }

  protected void closeAdditionalResources() {
    // Override in subclasses to close additional resources
  }

  protected void setupInfrastructure() {
    this.mySQLContainer =
        new MySQLContainer<>(
                System.getProperty(Constants.MYSQL_IMAGE_KEY, Constants.DEFAULT_MYSQL_IMAGE))
            .withDatabaseName(Constants.MYSQL_DATABASE)
            .withUsername(Constants.MYSQL_USER)
            .withPassword(Constants.MYSQL_PASSWORD);
    this.mySQLContainer.start();
    log.info("Started mysql container on port:{}", this.mySQLContainer.getFirstMappedPort());
    this.setMysqlSystemProperties();
    this.runDatabaseMigrations();

    DockerImageName localstackImage = DockerImageName.parse(Constants.LOCALSTACK_DOCKER_IMAGE);
    localStackContainer =
        new LocalStackContainer(localstackImage).withServices(LocalStackContainer.Service.SQS);
    localStackContainer.start();
    String port = localStackContainer.getFirstMappedPort().toString();
    log.info("Started localstack sqs container on port:{}", port);
    System.setProperty(Constants.AWS_ACCESS_KEY_ID, this.localStackContainer.getAccessKey());
    System.setProperty(Constants.AWS_SECRET_ACCESS_KEY, this.localStackContainer.getSecretKey());
    this.setSqsSystemProperties();
    this.setupAdditionalSystemProperties();
  }

  protected void setupAdditionalSystemProperties() {}

  protected void setupGrpcMock(ExtensionContext extensionContext) throws IOException {
    String serverName = InProcessServerBuilder.generateName();
    grpcCleanup.register(
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(new MockOAMProviderAccountService())
            .build()
            .start());
    RxProviderAccountServiceGrpc.RxProviderAccountServiceStub client =
        OdinAccountManagerClientFactory.getProviderAccountService(
            grpcCleanup.register(
                InProcessChannelBuilder.forName(serverName).directExecutor().build()));

    extensionContext.getRoot().getStore(GLOBAL).put("oam", client);
  }

  @SneakyThrows
  protected void loadTestConfig() {
    ConfigStoreOptions fileStore =
        new ConfigStoreOptions()
            .setType("file")
            .setFormat("hocon")
            .setConfig(new JsonObject().put("path", "application-default.conf"));

    // Add the system properties store to override the file config
    ConfigStoreOptions sysPropsStore =
        new ConfigStoreOptions()
            .setType("sys")
            .setConfig(new JsonObject().put("hierarchical", true));

    ConfigRetrieverOptions retrieverOptions =
        new ConfigRetrieverOptions().addStore(fileStore).addStore(sysPropsStore);

    ConfigRetriever retriever = ConfigRetriever.create(vertx, retrieverOptions);

    JsonObject configJson = retriever.rxGetConfig().blockingGet();

    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    AppConfig appConfig = objectMapper.readValue(configJson.encode(), AppConfig.class);
    SharedDataUtil.setInstance(vertx.getDelegate(), appConfig);
  }

  protected void startMockOrchestratorVerticle() {
    log.info("Starting mock orchestrator verticle");
    this.vertx
        .rxDeployVerticle(Constants.MOCK_ORCHESTRATOR_VERTICLE_NAME)
        .doOnError(
            error ->
                log.error(
                    "Error in deploying verticle : {}",
                    Constants.MOCK_ORCHESTRATOR_VERTICLE_NAME,
                    error))
        .doOnSuccess(
            v -> log.info("Deployed verticle : {}", Constants.MOCK_ORCHESTRATOR_VERTICLE_NAME))
        .blockingGet();
  }

  protected void startApplication() {
    log.info("Starting application for running integration tests");
    GuiceInjector injector =
        new GuiceInjector(
            Guice.createInjector(List.of(new TestModule(vertx.getDelegate(), extensionContext))));
    SharedDataUtil.setInstance(vertx.getDelegate(), injector);
    this.vertx
        .rxDeployVerticle(Constants.MAIN_VERTICLE_NAME)
        .doOnError(
            error ->
                log.error("Error in deploying verticle : {}", Constants.MAIN_VERTICLE_NAME, error))
        .doOnSuccess(v -> log.info("Deployed verticle : {}", Constants.MAIN_VERTICLE_NAME))
        .blockingGet();
  }

  protected void setMysqlSystemProperties() {
    for (String PREFIX : Constants.MYSQL_PREFIXES) {
      System.setProperty(PREFIX + Constants.MYSQL_HOST_KEY, this.mySQLContainer.getHost());
      System.setProperty(
          PREFIX + Constants.MYSQL_PORT_KEY,
          String.valueOf(this.mySQLContainer.getFirstMappedPort()));
      System.setProperty(
          PREFIX + Constants.MYSQL_DATABASE_KEY, this.mySQLContainer.getDatabaseName());
      System.setProperty(PREFIX + Constants.MYSQL_USER_KEY, this.mySQLContainer.getUsername());
      System.setProperty(PREFIX + Constants.MYSQL_PASSWORD_KEY, this.mySQLContainer.getPassword());
    }
    System.setProperty(Constants.APP_ENVIRONMENT, "test");
  }

  protected void setSqsSystemProperties() {
    System.setProperty(
        Constants.SQS_REQUEST_QUEUE_ENDPOINT,
        this.localStackContainer.getEndpointOverride(LocalStackContainer.Service.SQS).toString());
    System.setProperty(
        Constants.SQS_RESPONSE_QUEUE_ENDPOINT,
        this.localStackContainer.getEndpointOverride(LocalStackContainer.Service.SQS).toString());
    System.setProperty(Constants.SQS_REQUEST_QUEUE_REGION, this.localStackContainer.getRegion());
    System.setProperty(Constants.SQS_RESPONSE_QUEUE_REGION, this.localStackContainer.getRegion());
    SqsClient sqsClient =
        SqsClient.builder()
            .endpointOverride(URI.create(System.getProperty(Constants.SQS_REQUEST_QUEUE_ENDPOINT)))
            .region(Region.of(System.getProperty(Constants.SQS_REQUEST_QUEUE_REGION)))
            .build();

    CreateQueueRequest createQueueRequest =
        CreateQueueRequest.builder().queueName(Constants.SQS_REQUEST_QUEUE).build();
    System.setProperty(
        Constants.SQS_REQUEST_QUEUE_URL, sqsClient.createQueue(createQueueRequest).queueUrl());

    CreateQueueRequest createQueueRequestForResponse =
        CreateQueueRequest.builder().queueName(Constants.SQS_RESPONSE_QUEUE).build();
    System.setProperty(
        Constants.SQS_RESPONSE_QUEUE_URL,
        sqsClient.createQueue(createQueueRequestForResponse).queueUrl());
  }

  @SneakyThrows
  protected void runDatabaseMigrations() {
    log.info("Starting migrations on data sources");
    String dbUrl =
        String.format(
            "jdbc:mysql://%s:%d/%s",
            mySQLContainer.getHost(),
            mySQLContainer.getFirstMappedPort(),
            Constants.MYSQL_DATABASE);
    try (Connection conn =
        DriverManager.getConnection(
            dbUrl, mySQLContainer.getUsername(), mySQLContainer.getPassword())) {
      Database database =
          DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));

      try (Liquibase liquibase =
          new Liquibase(
              "db/mysql/db-migrate-changelog.xml", new ClassLoaderResourceAccessor(), database)) {
        liquibase.update((String) null);
      }
    }
  }

  protected void setDefaultRxSchedulers(Vertx vertx) {
    RxJavaPlugins.setComputationSchedulerHandler(s -> RxHelper.scheduler(vertx));
    RxJavaPlugins.setIoSchedulerHandler(s -> RxHelper.blockingScheduler(vertx));
    RxJavaPlugins.setNewThreadSchedulerHandler(s -> RxHelper.scheduler(vertx));
  }
}
