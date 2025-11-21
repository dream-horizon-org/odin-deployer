package com.dream11.odin.util;

import static com.dream11.odin.Constants.EMPTY_JSON;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dream11.odin.Constants;
import com.dream11.odin.FailedComponent;
import com.dream11.odin.auth.JwtConfig;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.contract.request.RequestMessage;
import com.dream11.odin.contract.request.environment.NamespaceRequestMessageBody;
import com.dream11.odin.contract.request.service.ServiceRequestMessageBody;
import com.dream11.odin.contract.response.ResponseData;
import com.dream11.odin.contract.response.ResponseMessage;
import com.dream11.odin.dto.v1.ComponentDefinition;
import com.dream11.odin.dto.v1.ComponentProvisioningConfig;
import com.dream11.odin.dto.v1.ProvisioningConfig;
import com.dream11.odin.dto.v1.ServiceDefinition;
import com.dream11.odin.grpc.service.DeployServiceRequest;
import com.dream11.odin.grpc.service.DeployServiceResponse;
import com.dream11.odin.grpc.service.ServiceResponse;
import com.dream11.queue.impl.sqs.SqsConfig;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.jsonwebtoken.Jwts;
import io.restassured.common.mapper.TypeRef;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.json.JsonObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.Inflater;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@UtilityClass
public class TestUtil {

  public static final String COMPONENT_FAILED_ERROR_MESSAGE = "Component failed";

  @SneakyThrows
  public static String generateAuthToken() {
    Config config = ConfigFactory.load("application-default.conf");
    Config jwtConfigObject = config.getConfig("authConfig.jwtConfig");

    JwtConfig jwtConfig =
        new JwtConfig(
            jwtConfigObject.getString("privateKey"),
            jwtConfigObject.getString("publicKey"),
            jwtConfigObject.getLong("expirationMillis"));

    PrivateKey privateKey = parsePrivateKey(jwtConfig.getPrivateKey());

    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + jwtConfig.getExpirationMillis());
    Map<String, Object> claims = new HashMap<>();
    claims.put("orgid", 1L);

    return Jwts.builder()
        .subject("anonymous")
        .issuer("AUTH.ODIN")
        .claims(claims)
        .issuedAt(now)
        .expiration(expiryDate)
        .signWith(privateKey, Jwts.SIG.ES256)
        .compact();
  }

  private static PrivateKey parsePrivateKey(String privateKeyPem) {
    try {
      String privateKeyContent =
          privateKeyPem
              .replaceAll("-----BEGIN PRIVATE KEY-----", "")
              .replaceAll("-----END PRIVATE KEY-----", "")
              .replaceAll("\\s", "");

      byte[] decodedKey = Base64.getDecoder().decode(privateKeyContent);

      KeyFactory keyFactory = KeyFactory.getInstance("EC");
      PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decodedKey);
      return keyFactory.generatePrivate(keySpec);
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new RuntimeException("Error parsing private key", e);
    }
  }

  public Connection getDatabaseConnection() throws SQLException {
    String mysqlMasterHost = Constants.MYSQL_PREFIXES.get(0);
    String url =
        String.format(
            "jdbc:mysql://localhost:%s/%s?autoReconnect=true&allowMultiQueries=true",
            System.getProperty(mysqlMasterHost + Constants.MYSQL_PORT_KEY),
            System.getProperty(mysqlMasterHost + Constants.MYSQL_DATABASE_KEY));

    return DriverManager.getConnection(
        url,
        System.getProperty(mysqlMasterHost + Constants.MYSQL_USER_KEY),
        System.getProperty(mysqlMasterHost + Constants.MYSQL_PASSWORD_KEY));
  }

  @SneakyThrows
  public void executeSqlFile(Connection connection, String fileName) {
    executeSqlFile(connection, Constants.TEST_DATA_DIRECTORY_PATH, fileName);
  }

  public static String readFileAndSanitizeLiquibaseIdentifiers(Path inputFile) throws IOException {
    return Files.lines(inputFile)
        .filter(
            line -> {
              String trimmed = line.trim().toLowerCase();
              return !trimmed.startsWith("--liquibase") && !trimmed.startsWith("--changeset");
            })
        .collect(Collectors.joining(System.lineSeparator()));
  }

  @SneakyThrows
  public void executeSqlFile(Connection connection, String dirName, String fileName) {
    String filePath = String.format("%s/%s", dirName, fileName);
    String content = readFileAndSanitizeLiquibaseIdentifiers(Path.of(filePath));
    executeSqlStatement(connection, content);
  }

  @SneakyThrows
  public void executeSqlFromDir(Connection connection, String dirName) {

    File folder = new File(dirName);
    File[] listOfFiles = folder.listFiles();
    if (listOfFiles != null) {

      Arrays.stream(listOfFiles)
          .filter(File::isFile)
          .forEach(
              fileName -> {
                String content;
                try {
                  content = readFileAndSanitizeLiquibaseIdentifiers(fileName.toPath());
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
                executeSqlStatement(connection, content);
              });
    } else {
      throw new RuntimeException("The directory is empty or it does not exist: " + dirName);
    }
  }

  @SneakyThrows
  public void executeSqlStatement(Connection connection, String query) {
    try (Statement statement = connection.createStatement()) {
      // start a transaction
      connection.setAutoCommit(false);
      statement.executeUpdate(query);
      connection.commit();
    } catch (SQLException e) {
      connection.rollback();
      throw e;
    } finally {
      connection.setAutoCommit(true);
    }
  }

  public void truncateDatabase(Connection connection) {
    executeSqlFile(connection, "Truncate.sql");
  }

  public DeployServiceRequest getDeployServiceRequest(String serviceName, String environmentName) {
    return getDeployServiceRequest(
        serviceName,
        environmentName,
        Constants.TEST_CONFIG_KEY,
        Constants.TEST_PARAM_KEY,
        Constants.TEST_DEPLOYMENT_TYPE,
        Constants.TEST_COMPONENT_NAME);
  }

  public Pair<ServiceDefinition, ProvisioningConfig> getServiceDefinitionAndProvisioningConfig(
      String serviceName,
      String serviceVersion,
      String configKey,
      String param,
      String deploymentType,
      String... componentNames) {

    List<ComponentDefinition> componentDefinitionList =
        Arrays.stream(componentNames)
            .map(
                componentName ->
                    ComponentDefinition.newBuilder()
                        .setName(componentName)
                        .setType(Constants.TEST_COMPONENT_TYPE)
                        .setVersion(Constants.TEST_VERSION)
                        .setConfig(
                            Struct.newBuilder()
                                .putFields(
                                    configKey,
                                    Value.newBuilder().setStringValue("testConfigValue").build())
                                .putFields(
                                    Constants.TEST_ARTIFACT_NAME_KEY,
                                    Value.newBuilder()
                                        .setStructValue(
                                            Struct.newBuilder()
                                                .putFields(
                                                    "name",
                                                    Value.newBuilder()
                                                        .setStringValue(
                                                            Constants.TEST_ARTIFACT_NAME)
                                                        .build())
                                                .putFields(
                                                    "version",
                                                    Value.newBuilder()
                                                        .setStringValue("1.0.0")
                                                        .build())
                                                .build())
                                        .build())
                                .build())
                        .build())
            .toList();

    List<ComponentProvisioningConfig> componentProvisioningConfigList =
        Arrays.stream(componentNames)
            .map(
                componentName ->
                    ComponentProvisioningConfig.newBuilder()
                        .setComponentName(componentName)
                        .setDeploymentType(deploymentType)
                        .setParams(
                            Struct.newBuilder()
                                .putFields(
                                    param,
                                    Value.newBuilder().setStringValue("testParamValue").build())
                                .putFields(
                                    Constants.TEST_TAG_KEY,
                                    Value.newBuilder()
                                        .setStructValue(convertMapToStruct(Map.of()))
                                        .build())
                                .build())
                        .build())
            .toList();

    return Pair.of(
        ServiceDefinition.newBuilder()
            .setName(serviceName)
            .setVersion(serviceVersion)
            .setTeam("devx")
            .addAllComponents(componentDefinitionList)
            .build(),
        ProvisioningConfig.newBuilder()
            .addAllComponentProvisioningConfig(componentProvisioningConfigList)
            .build());
  }

  public DeployServiceRequest getDeployServiceRequest(
      String serviceName,
      String environmentName,
      String configKey,
      String param,
      String deploymentType,
      String... componentNames) {

    Pair<ServiceDefinition, ProvisioningConfig> serviceDefinitionProvisioningConfigPair =
        TestUtil.getServiceDefinitionAndProvisioningConfig(
            serviceName, Constants.TEST_VERSION, configKey, param, deploymentType, componentNames);
    return DeployServiceRequest.newBuilder()
        .setEnvName(environmentName)
        .setServiceDefinition(serviceDefinitionProvisioningConfigPair.getLeft())
        .setProvisioningConfig(serviceDefinitionProvisioningConfigPair.getRight())
        .build();
  }

  @SneakyThrows
  public int createServiceTask(
      Connection connection,
      String serviceName,
      String environmentName,
      TaskStatus status,
      Action action) {
    return createServiceTask(connection, serviceName, environmentName, status, action, 1);
  }

  @SneakyThrows
  public int createServiceTask(
      Connection connection,
      String serviceName,
      String environmentName,
      TaskStatus status,
      Action action,
      int version) {
    String query =
        "INSERT INTO service_task(action_id, config, service_config_hash, env_id, name, service_version, status, created_by, updated_by, version)"
            + "VALUES ((SELECT action.id FROM action WHERE action.name = ?), ? , 'abc',"
            + "(SELECT environment.id FROM environment WHERE environment.name = ?), ?, '1.0.0', ?, '196', '196', ?);";

    // create service definition config
    JsonObject serviceConfig = new JsonObject().put("name", serviceName).put("version", "1.0.0");

    try (PreparedStatement preparedStatement =
        connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
      preparedStatement.setString(1, action.getName().toLowerCase());
      preparedStatement.setString(2, serviceConfig.toString());
      preparedStatement.setString(3, environmentName);
      preparedStatement.setString(4, serviceName);
      preparedStatement.setString(5, status.getValue());
      preparedStatement.setInt(6, version);
      assertThat(preparedStatement.executeUpdate()).isEqualTo(1);
      log.info(
          "Service task created successfully for service: {} in env: {}",
          serviceName,
          environmentName);
      ResultSet resultSet = preparedStatement.getGeneratedKeys();
      assertThat(resultSet.next()).isTrue();
      return resultSet.getInt(1);
    }
  }

  @SneakyThrows
  public void createComponentTask(
      Connection connection,
      Integer serviceTaskId,
      String componentName,
      JsonObject componentConfig,
      TaskStatus status,
      Action action) {
    String query =
        "INSERT INTO component_task (service_task_id, action_id, component_name, status, config, config_hash, response, "
            + "service_account_snapshot, created_by, "
            + "updated_by)"
            + "VALUES (?, (SELECT action.id FROM action WHERE action.name = ?), ?, ?, ?, ?, '{}','{}', '196', '196')";

    try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
      preparedStatement.setInt(1, serviceTaskId);
      preparedStatement.setString(2, action.getName());
      preparedStatement.setString(3, componentName);
      preparedStatement.setString(4, status.getValue());
      preparedStatement.setString(5, componentConfig.toString());
      preparedStatement.setString(6, DigestUtils.sha256Hex(componentConfig.toString()));
      assertThat(preparedStatement.executeUpdate()).isEqualTo(1);
      log.info(
          "Component task created successfully for component: {} in serviceTaskId: {}",
          componentName,
          serviceTaskId);
    }
  }

  public JsonObject getComponentConfig(
      String componentName,
      String configKey,
      String paramKey,
      String deploymentType,
      Map<String, String> tags) {
    ComponentDefinition componentDefinition =
        ComponentDefinition.newBuilder()
            .setName(componentName)
            .setType(Constants.TEST_COMPONENT_TYPE)
            .setVersion(Constants.TEST_VERSION)
            .setConfig(
                Struct.newBuilder()
                    .putFields(
                        configKey, Value.newBuilder().setStringValue("testConfigValue").build())
                    .putFields(
                        Constants.TEST_ARTIFACT_NAME_KEY,
                        Value.newBuilder().setStringValue(Constants.TEST_ARTIFACT_NAME).build())
                    .putFields(
                        Constants.TEST_ARTIFACT_NAME_KEY,
                        Value.newBuilder()
                            .setStructValue(
                                Struct.newBuilder()
                                    .putFields(
                                        "name",
                                        Value.newBuilder()
                                            .setStringValue(Constants.TEST_ARTIFACT_NAME)
                                            .build())
                                    .putFields(
                                        "version",
                                        Value.newBuilder().setStringValue("1.0.0").build())
                                    .build())
                            .build())
                    .build())
            .build();
    ComponentProvisioningConfig componentProvisioningConfig =
        ComponentProvisioningConfig.newBuilder()
            .setComponentName(componentName)
            .setDeploymentType(deploymentType)
            .setParams(
                Struct.newBuilder()
                    .putFields(
                        paramKey, Value.newBuilder().setStringValue("testParamValue").build())
                    .putFields(
                        Constants.TEST_TAG_KEY,
                        Value.newBuilder().setStructValue(convertMapToStruct(tags)).build())
                    .build())
            .build();
    return ComponentUtil.componentConfigToJson(
        componentDefinition, componentProvisioningConfig, null);
  }

  public JsonObject getComponentConfigWithDependsOn(
      String componentName,
      String configKey,
      String paramKey,
      String deploymentType,
      String dependsOn) {
    ComponentDefinition componentDefinition =
        ComponentDefinition.newBuilder()
            .setName(componentName)
            .setType(Constants.TEST_COMPONENT_TYPE)
            .setVersion(Constants.TEST_VERSION)
            .addDependsOn(dependsOn)
            .setConfig(
                Struct.newBuilder()
                    .putFields(
                        configKey, Value.newBuilder().setStringValue("testConfigValue").build())
                    .build())
            .build();
    ComponentProvisioningConfig componentProvisioningConfig =
        ComponentProvisioningConfig.newBuilder()
            .setComponentName(componentName)
            .setDeploymentType(deploymentType)
            .setParams(
                Struct.newBuilder()
                    .putFields(
                        paramKey, Value.newBuilder().setStringValue("testParamValue").build())
                    .build())
            .build();
    return ComponentUtil.componentConfigToJson(
        componentDefinition, componentProvisioningConfig, EMPTY_JSON);
  }

  @SneakyThrows
  public void assertComponentValidateTaskConfig(
      Connection connection, String componentName, JsonObject componentConfig) {
    ResultSet resultSet =
        fetchLatestComponentTask(connection, Action.VALIDATE.name().toLowerCase(), componentName);
    assertThat(resultSet.next()).isTrue();
    JsonObject dbConfig = new JsonObject(resultSet.getString("config"));
    assertThat(componentConfig).isEqualTo(dbConfig);
  }

  public static Struct convertMapToStruct(Map<String, String> map) {
    Struct.Builder structBuilder = Struct.newBuilder();

    for (Map.Entry<String, String> entry : map.entrySet()) {
      structBuilder.putFields(
          entry.getKey(), Value.newBuilder().setStringValue(entry.getValue()).build());
    }

    return structBuilder.build();
  }

  @SneakyThrows
  public ResultSet fetchLatestComponentTask(
      Connection connection, String stage, String componentName) {
    // Fetch component validate task config
    String queryTable =
        (stage.equalsIgnoreCase(Action.VALIDATE.name().toLowerCase()))
            ? "component_validate_task"
            : "component_task";
    String query =
        String.format("SELECT * FROM %s WHERE component_name = ? ORDER BY id DESC", queryTable);

    PreparedStatement preparedStatement = connection.prepareStatement(query);
    preparedStatement.setString(1, componentName);
    return preparedStatement.executeQuery();
  }

  @SneakyThrows
  public void assertComponentTaskConfig(
      Connection connection, String componentName, JsonObject componentConfig) {
    // Fetch component task config
    ResultSet resultSet =
        fetchLatestComponentTask(connection, Action.DEPLOY.name().toLowerCase(), componentName);
    assertThat(resultSet.next()).isTrue();
    JsonObject dbConfig = new JsonObject(resultSet.getString("config"));
    assertThat(dbConfig).isEqualTo(componentConfig);
  }

  public void assertComponentTaskStatus(
      Connection connection, String componentName, Action action, TaskStatus status)
      throws SQLException {
    // Fetch component task config
    ResultSet resultSet =
        fetchLatestComponentTask(connection, action.getName().toLowerCase(), componentName);
    assertThat(resultSet.next()).isTrue();
    TaskStatus dbStatus = TaskStatus.valueOf(resultSet.getString("status"));
    assertThat(dbStatus).isEqualTo(status);
  }

  public void assertLatestComponentTaskAction(
      Connection connection, String componentName, Action action) throws SQLException {
    // Fetch component task config
    String query =
        "SELECT name FROM action WHERE id=(SELECT action_id FROM component_task WHERE component_name = ? ORDER BY id DESC "
            + "LIMIT 1)";

    PreparedStatement preparedStatement = connection.prepareStatement(query);
    preparedStatement.setString(1, componentName);
    ResultSet resultSet = preparedStatement.executeQuery();
    assertThat(resultSet.next()).isTrue();
    assertThat(action).isEqualTo(Action.valueOf(resultSet.getString("name")));
  }

  @SneakyThrows
  public void assertUndeployDeployComponentTaskAction(Connection connection, String componentName) {
    // Fetch component task config
    String query =
        "SELECT action.name AS action_name, status FROM component_task JOIN action ON component_task.action_id = action.id WHERE "
            + "component_name = "
            + "? ORDER BY component_task.id DESC";

    PreparedStatement preparedStatement = connection.prepareStatement(query);
    preparedStatement.setString(1, componentName);
    ResultSet resultSet = preparedStatement.executeQuery();

    // Assert that latest task created is component deploy, preceded by an undeploy task
    assertThat(resultSet.next()).isTrue();
    assertThat(Action.valueOf(resultSet.getString("action_name"))).isEqualTo(Action.DEPLOY);
    assertThat(TaskStatus.valueOf(resultSet.getString("status"))).isEqualTo(TaskStatus.SUCCESSFUL);

    assertThat(resultSet.next()).isTrue();
    assertThat(Action.valueOf(resultSet.getString("action_name"))).isEqualTo(Action.UNDEPLOY);
    assertThat(TaskStatus.valueOf(resultSet.getString("status"))).isEqualTo(TaskStatus.SUCCESSFUL);
  }

  /*
  All service and component statuses are marked SUCCESSFUL in the mock response.
  To customize the response, add the service name and the corresponding component names in failedServiceComponents to mark them as failed.
  */
  public List<ResponseMessage> getMockOrchestratorServiceResponses(RequestMessage requestMessage) {
    ServiceRequestMessageBody serviceRequestMessageBody =
        (ServiceRequestMessageBody) requestMessage.getBody();
    String serviceName = serviceRequestMessageBody.getServiceName();

    List<ResponseMessage> responseMessages = new ArrayList<>();
    String serviceStageName;
    if (serviceRequestMessageBody.getComponentActions().stream()
        .anyMatch(
            componentAction ->
                componentAction.getStage().getName().equalsIgnoreCase(Action.VALIDATE.getName()))) {
      serviceStageName = Action.VALIDATE.getName();
    } else if (serviceRequestMessageBody.getComponentActions().stream()
        .anyMatch(
            componentAction ->
                componentAction.getStage().getName().equalsIgnoreCase(Action.OPERATE.getName()))) {
      serviceStageName = Action.OPERATE.getName();
    } else if (serviceRequestMessageBody.getComponentActions().stream()
        .anyMatch(
            componentAction ->
                componentAction.getStage().getName().equalsIgnoreCase(Action.DEPLOY.getName()))) {
      serviceStageName = Action.DEPLOY.getName();
    } else {
      serviceStageName = Action.UNDEPLOY.getName();
    }

    ((ServiceRequestMessageBody) requestMessage.getBody())
        .getComponentActions()
        .forEach(
            componentAction -> {
              TaskStatus componentStatus = TaskStatus.SUCCESSFUL;
              if (FailedComponent.containsFailedServiceComponent(
                      Pair.of(serviceName, Action.forAction(serviceStageName)))
                  && FailedComponent.getFailedComponents(
                          Pair.of(serviceName, Action.forAction(serviceStageName)))
                      .contains(
                          Pair.of(
                              componentAction.getName(),
                              Action.forAction(componentAction.getStage().getName())))) {
                componentStatus = TaskStatus.FAILED;
              }
              responseMessages.add(
                  ResponseMessage.builder()
                      .id(requestMessage.getId())
                      .type(com.dream11.odin.contract.response.ResponseMessageType.COMPONENT_STATUS)
                      .status(componentStatus)
                      .data(
                          ResponseData.builder()
                              .componentName(componentAction.getName())
                              .stage(componentAction.getStage().getName())
                              .build())
                      .error(
                          TaskStatus.FAILED.equals(componentStatus)
                              ? COMPONENT_FAILED_ERROR_MESSAGE
                              : null)
                      .build());
            });

    // Add service status
    TaskStatus serviceStatus = TaskStatus.SUCCESSFUL;
    if (FailedComponent.containsFailedServiceComponent(
        Pair.of(serviceName, Action.forAction(serviceStageName)))) {
      serviceStatus = TaskStatus.FAILED;
    }
    responseMessages.add(
        ResponseMessage.builder()
            .id(requestMessage.getId())
            .type(com.dream11.odin.contract.response.ResponseMessageType.SERVICE_STATUS)
            .data(ResponseData.builder().stage(serviceStageName).build())
            .status(serviceStatus)
            .build());
    return responseMessages;
  }

  public ResponseMessage getMockOrchestratorNamespaceResponses(RequestMessage requestMessage) {
    NamespaceRequestMessageBody serviceRequestMessageBody =
        (NamespaceRequestMessageBody) requestMessage.getBody();
    String envName = serviceRequestMessageBody.getName();

    return ResponseMessage.builder()
        .id(requestMessage.getId())
        .type(com.dream11.odin.contract.response.ResponseMessageType.NAMESPACE)
        .status(
            FailedComponent.containsFailedNamespace(envName)
                ? TaskStatus.FAILED
                : TaskStatus.SUCCESSFUL)
        .build();
  }

  public boolean checkActionInProgress(ServiceResponse response, Action action) {
    return response.getServiceStatus().getServiceAction().equalsIgnoreCase(action.getName())
        && response
            .getServiceStatus()
            .getServiceStatus()
            .equalsIgnoreCase(TaskStatus.IN_PROGRESS.getValue());
  }

  public boolean checkActionSuccess(ServiceResponse response, Action action) {
    return response.getServiceStatus().getServiceAction().equalsIgnoreCase(action.getName())
        && response
            .getServiceStatus()
            .getServiceStatus()
            .equalsIgnoreCase(TaskStatus.SUCCESSFUL.getValue())
        && response.getComponentsStatusList().stream()
            .allMatch(
                componentStatus ->
                    componentStatus
                        .getComponentStatus()
                        .equalsIgnoreCase(TaskStatus.SUCCESSFUL.getValue()));
  }

  public boolean checkActionFailed(ServiceResponse response, Action action) {
    return response.getServiceStatus().getServiceAction().equalsIgnoreCase(action.getName())
        && response
            .getServiceStatus()
            .getServiceStatus()
            .equalsIgnoreCase(TaskStatus.FAILED.getValue())
        && (response.getComponentsStatusList().isEmpty()
            || response.getComponentsStatusList().stream()
                .anyMatch(
                    componentStatus ->
                        componentStatus.getComponentAction().equalsIgnoreCase(action.getName())
                            && componentStatus
                                .getComponentStatus()
                                .equalsIgnoreCase(TaskStatus.FAILED.getValue())));
  }

  public static void assertServiceTaskStatus(
      Connection connection, String serviceName, Action action, TaskStatus taskStatus) {
    String query =
        "SELECT * FROM service_task  WHERE service_task.name = ? AND action_id = (SELECT id FROM action WHERE name = ?) order by created_at desc limit 1";
    try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
      preparedStatement.setString(1, serviceName);
      preparedStatement.setString(2, action.getName().toLowerCase());
      ResultSet resultSet = preparedStatement.executeQuery();
      assertThat(resultSet.next()).isTrue();
      assertThat(TaskStatus.valueOf(resultSet.getString("status"))).isEqualTo(taskStatus);
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  public static void assertComponentFailedMessage(
      DeployServiceResponse deployServiceResponse, String componentName) {
    assertTrue(
        deployServiceResponse.getServiceResponse().getComponentsStatusList().stream()
            .anyMatch(
                componentStatus ->
                    componentStatus.getComponentName().equals(componentName)
                        && componentStatus
                            .getError()
                            .replaceAll("\"", "")
                            .equalsIgnoreCase(COMPONENT_FAILED_ERROR_MESSAGE)));
  }

  @SneakyThrows
  public static String readMockData(String fileName) {
    return FileUtils.readFileToString(
        new File(Constants.TEST_MOCK_DATA_DIRECTORY_PATH + fileName), Charset.defaultCharset());
  }

  public static SqsConfig getSqsRequestConfig() {
    return SqsConfig.builder()
        .queueUrl(System.getProperty(Constants.SQS_REQUEST_QUEUE_URL))
        .region(System.getProperty(Constants.SQS_REQUEST_QUEUE_REGION))
        .endpoint("endpoint")
        .receiveConfig(SqsConfig.ReceiveConfig.builder().maxMessages(1).build())
        .build();
  }

  public static SqsConfig getSqsResponseConfig() {
    return SqsConfig.builder()
        .queueUrl(System.getProperty(Constants.SQS_RESPONSE_QUEUE_URL))
        .region(System.getProperty(Constants.SQS_REQUEST_QUEUE_REGION))
        .endpoint("endpoint")
        .receiveConfig(SqsConfig.ReceiveConfig.builder().maxMessages(1).build())
        .build();
  }

  private void deleteComponentTask(
      Connection connection,
      String serviceName2,
      String serviceVersion2,
      String environmentName,
      Action action) {
    String componentDeleteQuery =
        "DELETE FROM component_task WHERE service_task_id = (SELECT id FROM service_task WHERE name = ? AND "
            + "service_version = ? AND env_id = (SELECT id FROM environment WHERE name = ?) AND status ='SUCCESSFUL'  AND action_id = (SELECT"
            + " id FROM action WHERE name = ?) ORDER by created_at limit 1 )";
    try (PreparedStatement preparedStatement = connection.prepareStatement(componentDeleteQuery)) {
      preparedStatement.setString(1, serviceName2);
      preparedStatement.setString(2, serviceVersion2);
      preparedStatement.setString(3, environmentName);
      preparedStatement.setString(4, action.getName());
      preparedStatement.executeUpdate();
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  private void deleteServiceTask(
      Connection connection,
      String serviceName2,
      String serviceVersion2,
      String environmentName,
      Action action) {
    String serviceDeleteQuery =
        "DELETE FROM service_task WHERE name = ? AND service_version = ? AND env_id = (SELECT id FROM environment"
            + " WHERE name = ?) AND status ='SUCCESSFUL'  AND action_id = (SELECT id FROM action WHERE name = ?)";
    try (PreparedStatement preparedStatement = connection.prepareStatement(serviceDeleteQuery)) {
      preparedStatement.setString(1, serviceName2);
      preparedStatement.setString(2, serviceVersion2);
      preparedStatement.setString(3, environmentName);
      preparedStatement.setString(4, action.getName());
      preparedStatement.executeUpdate();
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  public static String decodeAndDecompress(String base64Data) {
    try {
      byte[] compressedData = Base64.getDecoder().decode(base64Data);
      Inflater inflater = new Inflater();
      inflater.setInput(compressedData);

      byte[] buffer = new byte[1024];
      int decompressedDataLength;

      // Decompress the data
      try (java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream()) {
        while (!inflater.finished()) {
          decompressedDataLength = inflater.inflate(buffer);
          outputStream.write(buffer, 0, decompressedDataLength);
        }
        return outputStream.toString(StandardCharsets.UTF_8);
      } finally {
        inflater.end();
      }
    } catch (Exception e) {
      log.error("Error while decompressing data {}", e.getMessage(), e);
      return null;
    }
  }

  public ComponentDefinition getComponentDefinition(String id) {
    return ComponentDefinition.newBuilder()
        .setName("c-%s".formatted(id))
        .setType("t-%s".formatted(id))
        .setVersion("v-%s".formatted(id))
        .build();
  }

  public ComponentProvisioningConfig getComponentProvisioningConfig(String id) {
    return ComponentProvisioningConfig.newBuilder()
        .setComponentName("c-%s".formatted(id))
        .setDeploymentType("d-%s".formatted(id))
        .setParams(Struct.newBuilder().build())
        .build();
  }

  public ValidatableResponse execute(
      Map<String, Object> body,
      Map<String, String> headers,
      Map<String, String> queryParams,
      Function<RequestSpecification, Response> fn) {
    RequestSpecification spec = given();
    if (body != null) {
      spec.header("Content-type", "application/json").and().body(body);
    }

    if (headers != null) {
      spec.headers(headers);
    }

    if (queryParams != null) {
      spec.queryParams(queryParams);
    }
    return fn.apply(spec).then();
  }

  public JsonObject extractBody(ValidatableResponse response) {
    return JsonObject.mapFrom(response.extract().as(new TypeRef<>() {}));
  }

  public JsonObject getErrorResponse(String message, String cause, String code) {
    return JsonObject.of("error", JsonObject.of("message", message, "cause", cause, "code", code));
  }

  public static void updateServiceStatus(
      Connection connection, int taskId, TaskStatus previousStatus, TaskStatus newStatus) {

    String updateServiceTaskQuery =
        "UPDATE service_task SET status = ? WHERE id = ? AND status = ?";
    String updateComponentTaskQuery =
        "UPDATE component_task SET status = ? WHERE service_task_id = ? AND status = ?";

    try (PreparedStatement updateServiceTaskStmt =
            connection.prepareStatement(updateServiceTaskQuery);
        PreparedStatement updateComponentTaskStmt =
            connection.prepareStatement(updateComponentTaskQuery)) {

      updateServiceTaskStmt.setString(1, newStatus.name());
      updateServiceTaskStmt.setInt(2, taskId);
      updateServiceTaskStmt.setString(3, previousStatus.name());

      updateComponentTaskStmt.setString(1, newStatus.name());
      updateComponentTaskStmt.setInt(2, taskId);
      updateComponentTaskStmt.setString(3, previousStatus.name());

      updateServiceTaskStmt.executeUpdate();
      updateComponentTaskStmt.executeUpdate();
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }
}
