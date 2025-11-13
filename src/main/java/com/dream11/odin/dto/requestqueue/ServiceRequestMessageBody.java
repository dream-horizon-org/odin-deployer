package com.dream11.odin.dto.requestqueue;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ServiceRequestMessageBody {

  List<ComponentAction> componentActions;
  String environmentName;
  String serviceName;
  long orgId;
}
