package com.dream11.odin.contract.response;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class ResponseData {
  String componentName;
  String stage;
}
