package com.dream11.odin.dto;

import com.dream11.odin.dto.v1.Environment;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RequestMetaContext {
  String serviceName;
  Environment environment;
  UserDetails userDetails;
  Map<String, Object> additionalContext;
}
