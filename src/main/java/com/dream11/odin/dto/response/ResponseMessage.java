package com.dream11.odin.dto.response;

import com.dream11.odin.constant.TaskStatus;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
@AllArgsConstructor
@Getter
@ToString
@Builder
public class ResponseMessage {
  Long id;
  ResponseMessageType type;
  String executionId;
  TaskStatus status;
  String error;
  Map<String, Object> data = new HashMap<>();
}
