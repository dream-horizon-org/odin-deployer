package com.dream11.odin.service.messagestrategy;

import static com.dream11.odin.constant.Constants.VALIDATE_NAMESPACE;

import com.dream11.odin.constant.Action;
import com.dream11.odin.dto.requestqueue.ServiceRequestQueueMessage;
import com.dream11.odin.dto.requestqueue.Stage;
import com.dream11.odin.util.ComponentUtil;
import com.dream11.odin.util.ServiceUtil;
import java.util.Map;
import java.util.stream.Collectors;

public class ValidateMessageBuilderStrategy implements MessageBuilderStrategy {
  public static final Map<String, Object> validateStageConfigForDeploy =
      Map.of("stageName", "deploy");

  @Override
  public ServiceRequestQueueMessage buildMessage(MessageBuilderPojo messageBuilderPojo) {

    Map<String, Stage> validateStageOverride =
        messageBuilderPojo.getComponentDataMap().entrySet().stream()
            .collect(
                Collectors.toMap(
                    e -> e.getKey().getComponentName(),
                    e ->
                        Stage.builder()
                            .name(Action.VALIDATE)
                            .config(validateStageConfigForDeploy)
                            .build()));

    return ServiceUtil.createPayload(
        messageBuilderPojo.getServiceName(),
        VALIDATE_NAMESPACE,
        ComponentUtil.getComponentActions(
            messageBuilderPojo.getComponentDataMap(), validateStageOverride),
        messageBuilderPojo.getServiceId(),
        messageBuilderPojo.getOrgId());
  }
}
