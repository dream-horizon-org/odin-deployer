package com.dream11.odin.dto.requestqueue;

import com.dream11.odin.constant.Action;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.With;

@Data
@Getter
@Builder
public class Stage {
  @With Action name;
  Map<String, Object> config;
}
