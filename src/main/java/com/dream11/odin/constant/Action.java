package com.dream11.odin.constant;

import static com.dream11.odin.error.OdinError.INTERNAL_SERVER_ERROR;

import com.dream11.grpc.util.ExceptionUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum Action {
  @JsonProperty("create_environment")
  CREATE_ENVIRONMENT("CREATE_ENVIRONMENT"),
  @JsonProperty("delete_environment")
  DELETE_ENVIRONMENT("DELETE_ENVIRONMENT"),
  @JsonProperty("deploy")
  DEPLOY("DEPLOY"),
  @JsonProperty("undeploy")
  UNDEPLOY("UNDEPLOY"),
  @JsonProperty("operate")
  OPERATE("OPERATE"),
  @JsonProperty("validate")
  VALIDATE("VALIDATE"),
  @JsonProperty("healthcheck")
  HEALTHCHECK("HEALTHCHECK");
  private final String name;

  public static Action forAction(String name) {
    return Arrays.stream(values())
        .filter(action -> action.name.equalsIgnoreCase(name))
        .findFirst()
        .orElseThrow(() -> ExceptionUtil.getException(INTERNAL_SERVER_ERROR));
  }

  @Override
  public String toString() {
    return this.name.toLowerCase();
  }
}
