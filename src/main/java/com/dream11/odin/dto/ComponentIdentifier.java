package com.dream11.odin.dto;

import com.dream11.odin.constant.Action;
import lombok.Builder;
import lombok.Data;
import lombok.With;

@Builder
@Data
public class ComponentIdentifier {
  String componentName;
  @With Action action;
}
