package com.dream11.odin.dto.response;

import lombok.Data;

@Data
public class ServiceResponseData implements ResponseData {
  String componentName;
  String stage;
}
