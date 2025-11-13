package com.dream11.odin.contract.request.environment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum NamespaceAction {
  @JsonProperty("create_environment")
  CREATE_ENVIRONMENT,

  @JsonProperty("delete_environment")
  DELETE_ENVIRONMENT
}
