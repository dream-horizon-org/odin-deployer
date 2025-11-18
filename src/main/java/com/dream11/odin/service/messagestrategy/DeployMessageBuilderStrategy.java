package com.dream11.odin.service.messagestrategy;

import com.dream11.odin.dto.requestqueue.ServiceRequestQueueMessage;
import com.dream11.odin.util.ComponentUtil;
import com.dream11.odin.util.ServiceUtil;
import java.util.Map;

public class DeployMessageBuilderStrategy implements MessageBuilderStrategy {
  @Override
  public ServiceRequestQueueMessage buildMessage(MessageBuilderPojo messageBuilderPojo) {
    return ServiceUtil.createPayload(
        messageBuilderPojo.getServiceName(),
        messageBuilderPojo.getEnvName(),
        ComponentUtil.getComponentActions(messageBuilderPojo.getComponentDataMap(), Map.of()),
        messageBuilderPojo.getServiceId(),
        messageBuilderPojo.getOrgId());
  }
}
