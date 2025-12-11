package com.dream11.odin.dto;

import com.dream11.odin.dto.v1.AccountInformation;
import com.dream11.odin.dto.v1.ComponentDefinition;
import com.dream11.odin.dto.v1.ComponentProvisioningConfig;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.protobuf.Struct;
import lombok.Builder;
import lombok.Data;
import lombok.With;

@Data
@Builder
public class ComponentData {
  @JsonProperty("definition")
  ComponentDefinition componentDefinition;

  @JsonProperty("provisioning")
  ComponentProvisioningConfig componentProvisioningConfig;

  @With String operationConfigJson;
  @With Struct stageConfig;

  @JsonProperty("accountInformation")
  AccountInformation environmentProviderAccounts;
}
