package com.dream11.odin.service.messagestrategy;

import com.dream11.odin.dto.ComponentData;
import com.dream11.odin.dto.ComponentIdentifier;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class MessageBuilderPojo {
  String envName;
  String serviceName;
  long orgId;
  long serviceId;
  Map<ComponentIdentifier, ComponentData> componentDataMap;
}
