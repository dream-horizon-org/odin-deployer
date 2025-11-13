package com.dream11.odin.dto;

import com.dream11.odin.dto.v1.ComponentProvisioningConfig;
import com.dream11.odin.dto.v1.ServiceDefinition;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ServiceData {
  ServiceDefinition serviceDefinition;
  List<ComponentProvisioningConfig> componentProvisioningConfigs;
}
