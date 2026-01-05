package com.dream11.odin.dto;

import com.dream11.odin.entity.EnvironmentEntity;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RequestMetaContext {
  String serviceName;
  EnvironmentEntity environment;
  UserDetails userDetails;
  Map<String, Object> additionalContext;
}
