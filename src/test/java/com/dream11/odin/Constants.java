package com.dream11.odin;

import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Constants {

  public final List<String> MYSQL_PREFIXES = Arrays.asList("mysql.master.", "mysql.slave.");
  public final String MYSQL_HOST_KEY = "connectOptions.host";
  public final String MYSQL_PORT_KEY = "connectOptions.port";
  public final String MYSQL_DATABASE_KEY = "connectOptions.database";
  public final String MYSQL_USER_KEY = "connectOptions.user";
  public final String MYSQL_PASSWORD_KEY = "connectOptions.password";
  public final String MYSQL_DATABASE = "odin_deployer_test";
  public final String MYSQL_USER = "test_user";
  public final String MYSQL_PASSWORD = "test_password";
  public final String MYSQL_IMAGE_KEY = "mysql.image";
  public final String DEFAULT_MYSQL_IMAGE = "mysql:8.0.35";
  public final String APP_ENVIRONMENT = "app.environment";
  public final String MAIN_VERTICLE_NAME = "com.dream11.odin.verticle.MainVerticle";
  public final String MOCK_ORCHESTRATOR_VERTICLE_NAME =
      "com.dream11.odin.verticle.MockOrchestratorVerticle";
  public static final int POLLING_FREQUENCY_MILLISECOND = 500;
  public final String LOCALSTACK_DOCKER_IMAGE = "localstack/localstack:1.3.0";
  public final String SQS_REQUEST_QUEUE = "request_queue";
  public final String SQS_RESPONSE_QUEUE = "response_queue";
  public final String SQS_REQUEST_QUEUE_ENDPOINT = "queue.request.endpoint";
  public final String SQS_RESPONSE_QUEUE_ENDPOINT = "queue.response.endpoint";
  public final String SQS_REQUEST_QUEUE_REGION = "queue.request.region";

  public final String SQS_RESPONSE_QUEUE_REGION = "queue.response.region";

  public final String SQS_REQUEST_QUEUE_URL = "queue.request.queueUrl";

  public final String SQS_RESPONSE_QUEUE_URL = "queue.response.queueUrl";
  public final String INTERCEPTORS_CONFIG_TIMEOUT = "interceptors.config.timeout";
  public final String INTERCEPTORS_CONFIG_RETRY_COUNT = "interceptors.config.retryCount";
  public final String INTERCEPTORS_CONFIG_COMPONENT = "interceptors.component";

  public final String TEST_DATA_DIRECTORY_PATH = "src/test/resources/data";
  public final String TEST_MOCK_DATA_DIRECTORY_PATH = "src/test/resources/mocks/";

  public final String CORE_SEED_DATA_DIR = "src/main/resources/db/mysql/seed/";

  public final String TEST_COMPONENT_NAME = "test-component";
  public final String TEST_SERVICE_NAME = "test-service";

  public final String TEST_ENV_NAME = "env1";
  public final String TEST_CONFIG_KEY = "testConfig";
  public final String TEST_PARAM_KEY = "testParam";
  public final String TEST_DEPLOYMENT_TYPE = "aws_ec2";
  public final String TEST_COMPONENT_TYPE = "test-component-type";
  public final String TEST_VERSION = "1.0.0";
  public final String TEST_ARTIFACT_NAME_KEY = "artifact";

  public final String TEST_TAG_KEY = "tags";
  public final String TEST_ARTIFACT_NAME = "testArtifact";
  public static final String SERVICE_OPERATION_ADD_COMPONENT = "add_component";
  public static final String TEST_TRACE_ID = "testTraceId";

  public static final String AWS_ACCESS_KEY_ID = "aws.accessKeyId";
  public static final String AWS_SECRET_ACCESS_KEY = "aws.secretAccessKey";
  public static final String EMPTY_JSON = "{}";
}
