package com.dream11.odin.constant;

import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Constants {
  public static final String MYSQL_CLIENT = "__mysql_client__";
  public static final String NAME = "name";
  public static final String WEB_CLIENT = "__web_client__";
  public static final String MESSAGE_CONSUMER = "__message_consumer__";
  public static final String MESSAGE_PRODUCER = "__message_producer__";
  public static final String UPDATED_AT = "updated_at";
  public static final String CREATED_AT = "created_at";
  public static final String SCHEMA_PARSER = "__schema_parser__";
  public static final List<String> AUTH_SKIPPED_SERVICES =
      List.of(
          "grpc.reflection.v1alpha.ServerReflection/ServerReflectionInfo",
          "dream11.od.auth.v1.AuthService/GetUserToken",
          "dream11.od.auth.v1.AuthService/GetAuthProvider",
          "grpc.health.v1.Health/Check",
          "grpc.health.v1.Health/Watch");
  public static final String USER_DETAILS = "user_details";
  public static final String USER_ID_PARAM = "user_id";
  public static final String ORG_ID_PARAM = "org_id";
  public static final String USER_EMAIL_PARAM = "user_email";
  public static final String DISPLAY_ALL_PARAM = "displayAll";
  public static final String ACCOUNT_PARAM = "account";
  public static final String SERVICE_NAME_PARAM = "service";
  public static final String COMPONENT_NAME_PARAM = "component";
  public static final String VALIDATE_NAMESPACE = "odin-validate";

  public static final int RECEIVE_WAIT_TIMEOUT_SECONDS = 10;
  public static final String PROVIDER_ACCOUNT_SERVICE = "__provider_account_service__";
  public static final String ORG_ID_HEADER = "orgId";
  public static final String EKS_PROVIDER_SERVICE_CATEGORY = "KUBERNETES";
  public static final String SERVICE_NAME = "service_name";
  public static final String STATUS = "status";
  public static final String ID = "id";
  public static final String ORGID = "orgid";

  public static final String ERROR_MESSAGE_FORMAT = "Error { %s }";
  // Validation constants
  public static final String DEFINITION_CONFIG_KEY = "definitionConfig";
  public static final String COMPONENT_CONFIG_KEY = "componentConfig";
  public static final String PROVISIONING_CONFIG_KEY = "provisioningConfig";
  public static final String OPERATION_CONFIG_KEY = "operationConfig";
  public static final String AUTHORIZATION_KEY = "Authorization";

  public static final String COL_COMPONENT_CONFIG = "component_config";
  public static final String COL_CONFIG = "config";
  public static final String COL_SERVICE_CONFIG_HASH = "service_config_hash";
  public static final String COL_SERVICE_VERSION = "service_version";
  public static final String COL_ACTION_NAME = "action_name";
  public static final String COL_ACTIONS = "actions";
  public static final String COL_CREATED_BY = "created_by";
  public static final String COL_UPDATED_BY = "updated_by";

  public static final String COL_VERSION = "version";
  public static final String STAGE = "stage";
  public static final String USE_OPERATE = "use operate";
  public static final String UNDEPLOY_AGAIN = "undeploy again";
  public static final String GRPC_VERTICLE = "com.dream11.odin.verticle.GrpcVerticle";
  public static final String CONSUMER_VERTICLE = "com.dream11.odin.verticle.ConsumerVerticle";
  public static final String REST_VERTICLE = "com.dream11.odin.verticle.RestVerticle";
  public static final String COMPONENT_NAME_UNIQUE = "components names should be unique";
  public static final String SERVICES_UNDEPLOY_FAILED = "services undeploy failed in environment";

  public static final String EXTRA_ENV_VARS = "extraEnvVars";

  public static final String SERVICE_OPERATION_ADD_COMPONENT = "add_component";
  public static final String SERVICE_OPERATION_REMOVE_COMPONENT = "remove_component";
  public static final String DEPLOY_ACTION = "deploy";
  public static final String VALIDATE_ACTION = "validate";
  public static final String OPERATE_ACTION = "operate";
  public static final String ACTION = "action";
  public static final String OPERATION = "operation";
  public static final String COMPONENT_NAME = "componentName";
  public static final String LEGACY_COMPONENT_NAME = "component_name";
  public static final String TRACE_ID = "TRACE_ID";
  public static final String COMPONENT_DEFINITION = "componentDefinition";
  public static final String OPERATION_NAME = "operationName";
  public static final String ANSI_DEFAULT = "\u001B[0m";
  public static final String SEARCH_AFTER_PARAM = "search_after_param";

  public static final String ODIN_COMPONENT_NAME = "ODIN_COMPONENT_NAME";
  public static final String ODIN_COMPONENT_TYPE = "ODIN_COMPONENT_TYPE";
  public static final String ODIN_ENV_NAME = "ODIN_ENV_NAME";
  public static final String ODIN_RESOURCE_TYPE = "ODIN_RESOURCE_TYPE";
  public static final String ODIN_SERVICE_NAME = "ODIN_SERVICE_NAME";
  public static final String ODIN_USER = "ODIN_USER";
  public static final String ODIN_COMPONENT_VERSION = "ODIN_COMPONENT_VERSION";

  public static final String OIDC = "oidc";
  public static final String ID_TOKEN = "id_token";
  public static final String EMAIL = "email";
  public static final String GRANT_TYPE = "grant_type";
  public static final String AUTHORIZATION_CODE = "authorization_code";
  public static final String CLIENT_ID = "client_id";
  public static final String CLIENT_SECRET = "client_secret";
  public static final String TOKEN_URL = "token_url";
  public static final String REDIRECT_URI = "redirect_uri";
  public static final String CODE = "code";

  public static final String CONTENT_TYPE = "Content-Type";
  public static final String APPLICATION_FORM_TYPE = "application/x-www-form-urlencoded";
}
