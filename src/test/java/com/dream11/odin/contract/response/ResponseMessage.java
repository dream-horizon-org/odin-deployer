package com.dream11.odin.contract.response;

import com.dream11.odin.constant.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class ResponseMessage {
  String error;

  Long id;

  TaskStatus status;

  ResponseMessageType type;

  ResponseData data;
}
