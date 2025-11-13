package com.dream11.odin.dto.interceptor;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StageContext {
  String name;
  Map<String, Object> config;
}
