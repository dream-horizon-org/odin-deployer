package com.dream11.odin.dto.requestqueue;

import com.dream11.odin.dto.constants.RequestMessageType;
import com.dream11.odin.injector.GuiceInjector;
import com.dream11.odin.util.ApplicationUtil;
import com.dream11.odin.util.SharedDataUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;

@Data
@Builder
public class ServiceRequestQueueMessage {
  Long id;
  RequestMessageType type;
  ServiceRequestMessageBody body;
  String traceId;

  @SneakyThrows
  public String toJsonString() {
    return SharedDataUtil.getInstance(GuiceInjector.class)
        .getInstance(ObjectMapper.class)
        .writeValueAsString(this);
  }

  @SneakyThrows
  public String compressMessage() {
    return ApplicationUtil.compressAndEncode(toJsonString());
  }
}
