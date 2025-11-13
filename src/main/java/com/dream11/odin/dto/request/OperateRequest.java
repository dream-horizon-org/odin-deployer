package com.dream11.odin.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.Getter;

@Data
@Getter
public class OperateRequest {
  @NotBlank private String operationName;
  private Map<String, Object> config = new HashMap<>();
}
