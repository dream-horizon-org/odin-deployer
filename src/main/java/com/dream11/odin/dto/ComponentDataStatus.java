package com.dream11.odin.dto;

import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.TaskStatus;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class ComponentDataStatus {
  ComponentData componentData;
  Action action;
  TaskStatus status;
}
