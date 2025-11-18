package com.dream11.odin.service.messagestrategy;

import com.dream11.odin.dto.requestqueue.ServiceRequestQueueMessage;

public interface MessageBuilderStrategy {

  ServiceRequestQueueMessage buildMessage(MessageBuilderPojo messageBuilderPojo);
}
