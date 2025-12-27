package com.dream11.odin.error;

import com.dream11.grpc.error.GrpcError;
import com.google.rpc.Code;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@RequiredArgsConstructor
public enum OdinError implements GrpcError {
  INTERNAL_SERVER_ERROR("INTERNAL-SERVER-ERROR", "Internal server error", Code.INTERNAL),
  INVALID_USER_LOGIN("OD102", "Access Denied", Code.PERMISSION_DENIED),
  ENV_NAME_MISSING("OD103", "Please provide env_name param", Code.INVALID_ARGUMENT),
  ENV_DOES_NOT_EXIST("OD104", "Environment with name:%s does not exist", Code.NOT_FOUND),
  SERVICE_DOES_NOT_EXIST_IN_ENV(
      "OD105", "Service with name:%s does not exist in env:%s", Code.NOT_FOUND),
  COMPONENT_DOES_NOT_EXIST_IN_SERVICE(
      "OD106", "Component with name:%s does not exist in service:%s", Code.NOT_FOUND),
  PROVIDE_BOTH_SERVICE_AND_COMPONENT(
      "OD107", "Service name should be provided along with component name", Code.INVALID_ARGUMENT),
  CONFLICTING_UPDATE(
      "OD113", "Your request failed due to an update conflict. Please try again.", Code.ABORTED),
  ENV_ALREADY_EXISTS(
      "OD119", "Env already exists with this name in state: %s.", Code.INVALID_ARGUMENT),

  ENV_CREATION_FAILED("OD120", "Env creation failed for %s.", Code.INTERNAL),

  INVALID_ENV_NAME(
      "OD122",
      "Invalid environment name [%s]. Environment name must be 1 to 9 characters long, start and end with a lowercase letter or number, "
          + "and contain only lowercase letters, numbers, and hyphens (-).",
      Code.INVALID_ARGUMENT),

  ENV_WITH_ID_DOES_NOT_EXIST("OD124", "Environment with id:%s not found", Code.NOT_FOUND),

  SERVICE_OR_COMPONENT_DOES_NOT_EXIST_IN_ENV(
      "OD125",
      "Service with name:%s or Component with name:%s does not exist in env:%s",
      Code.NOT_FOUND),

  ENV_NOT_RUNNING(
      "OD126", "Env [%s] not in RUNNING state, current env state: [%s]", Code.INVALID_ARGUMENT),

  DUPLICATE_SERVICE_DEPLOYMENT(
      "OD130", "Deployment already running with different configuration", Code.INVALID_ARGUMENT),

  CANNOT_DEPLOY_OVER_ACTION_WITH_STATUS(
      "OD136",
      "Unable to deploy service [%s] while [%s] is with status [%s]. Please %s",
      Code.INVALID_ARGUMENT),

  INVALID_ACTION("OD137", "Invalid action [%s]", Code.INVALID_ARGUMENT),

  COMPONENT_NOT_FOUND("OD139", "Component not found in service definition", Code.INVALID_ARGUMENT),
  INVALID_SERVICE_SNAPSHOT(
      "OD142",
      "Invalid service snapshot, cannot be parsed to a provider account response",
      Code.INTERNAL),

  ENV_DELETION_FAILED("OD143", "Env deletion failed for %s.", Code.INTERNAL),

  ENV_CANNOT_BE_DELETED("OD144", "Env cannot be deleted in state: %s.", Code.INVALID_ARGUMENT),

  FAILED_TO_FETCH_ACCOUNT_INFORMATION(
      "OD147", "Fetching account information failed with error: %s.", Code.INTERNAL),

  CYCLIC_DEPENDENCY_PRESENT_IN_SERVICE_DEFINITION(
      "OD148", "Cyclic dependency present in service definition", Code.INVALID_ARGUMENT),

  INVALID_SERVICE_DEFINITION_FILE(
      "OD149", "Invalid service definition file. Error: %s", Code.INVALID_ARGUMENT),

  INVALID_PROVISIONING_CONFIG_FILE(
      "OD150", "Invalid provisioning config file. Error: %s", Code.INVALID_ARGUMENT),

  INVALID_COMPONENT_IN_PROVISIONING_FILE(
      "OD151", "Invalid component name [%s] in provisioning config", Code.INVALID_ARGUMENT),

  MULTIPLE_VALIDATION_EXCEPTION("OD152", "%s", Code.INVALID_ARGUMENT),
  PROVISIONING_CONFIG_NOT_FOUND(
      "OD153",
      "Provisioning config for component(s) [%s] not found in provisioning file",
      Code.INVALID_ARGUMENT),
  SERVICE_CANNOT_BE_OPERATED(
      "OD156", "Service %s cannot be operated in state %s with status %s", Code.INVALID_ARGUMENT),
  SERVICE_CANNOT_BE_UNDEPLOYED(
      "OD161", "Service %s cannot be undeployed in state %s with status %s", Code.INVALID_ARGUMENT),
  COMPONENT_CANNOT_BE_OPERATED(
      "OD162", "Component %s cannot be operated in state %s with status %s", Code.INVALID_ARGUMENT),
  COMPONENT_CANNOT_BE_ADDED(
      "OD163",
      "Component %s cannot be added due to state %s with status %s",
      Code.INVALID_ARGUMENT),
  INVALID_SERVICE_OPERATION(
      "OD164",
      "Invalid service operation %s. Supported operations are add_component, remove_component",
      Code.INVALID_ARGUMENT),
  COMPONENT_NOT_FOUND_IN_OPERATION_INPUT(
      "OD165", "Component %s not found in operation input", Code.INVALID_ARGUMENT),

  ACCOUNT_NOT_FOUND_FOR_COMPONENT_VALIDATION(
      "OD181", "No account found for provider type: %s", Code.INVALID_ARGUMENT),

  PROVIDER_NOT_FOUND_FOR_ENV("OD195", "Provider not found, providerName: [%s]", Code.INTERNAL),

  INVALID_COMPONENT_DEPLOYMENT_TYPE("OD196", "Invalid deployment type %s", Code.INVALID_ARGUMENT),

  DUPLICATE_SERVICE_ACTION(
      "OD206",
      "Conflicting action[deploy/operate] already running for this service, please retry.",
      Code.ALREADY_EXISTS),
  OPERATION_IN_PROGRESS_DIFFERENT_CONFIGURATION(
      "OD213",
      "Error: Operation already in progress with different configuration",
      Code.INVALID_ARGUMENT),
  LOGS_NOT_FOUND("OD218", "Logs not found for traceId: %s", Code.NOT_FOUND),
  OPERATION_IN_PROGRESS(
      "OD220", "Error: Another Operation already in progress", Code.ALREADY_EXISTS),
  ONE_OR_MORE_SERVICE_IN_NON_TERMINAL_STATE(
      "OD220",
      "Env cannot be deleted, one or more service in non-terminal state",
      Code.INVALID_ARGUMENT),

  AUTH_PROVIDER_NOT_FOUND("OD221", "Auth provider not found for orgId: %s", Code.INVALID_ARGUMENT),
  AUTH_CODE_NOT_FOUND("OD222", "Authorization code not found for orgId: %s", Code.INVALID_ARGUMENT),
  AUTH_REDIRECT_URL_NOT_FOUND(
      "OD223", "Redirect url for Authorization not found for orgId: %s", Code.INVALID_ARGUMENT),
  AUTH_CLIENT_CREDENTIALS_NOT_FOUND(
      "OD224",
      "Client credentials for token exchange not found for orgId: %s",
      Code.INVALID_ARGUMENT),
  AUTH_ID_TOKEN_NOT_FOUND("OD225", "id_token not found in OIDC response", Code.INVALID_ARGUMENT),
  AUTH_ID_TOKEN_INVALID("OD226", "Invalid id_token format", Code.INVALID_ARGUMENT),
  AUTH_EMAIL_NOT_FOUND("OD227", "Email claim not found in id_token", Code.INVALID_ARGUMENT),
  AUTH_ID_TOKEN_PARSE_FAILED("OD228", "Failed to decode id_token payload", Code.INTERNAL),
  AUTH_TOKEN_EXCHANGE_FAILED("OD229", "Token exchange failed with status %s: %s", Code.INTERNAL),
  AUTH_TOKEN_RESPONSE_PARSE_FAILED(
      "OD230", "Failed to parse token response from provider", Code.INTERNAL),
  EXECUTION_NOT_FOUND("OD231", "Execution not found for executionId: %s", Code.INVALID_ARGUMENT),

  FAILED_TO_ACQUIRE_LOCK("OD232", "Failed to acquire lock on %s", Code.INVALID_ARGUMENT),
  ANOTHER_OPERATION_IN_PROGRESS(
      "OD233", "Failed to %s, another operation is in progress", Code.INVALID_ARGUMENT),
  NO_ROWS_UPDATED("OD235", "No rows were updated. Details: table: %s, params:%s", Code.INTERNAL),
  INVALID_NAMESPACE_RESPONSE_DATA(
      "OD235", "Invalid namespace response data. Error: %s", Code.INTERNAL),
  ENV_ACCOUNT_DOES_NOT_EXIST(
      "OD236", "Environment account with id:%s does not exist", Code.NOT_FOUND),
  ;

  private final String errorCode;
  private final String errorMessage;
  private final Code grpcCode;
}
