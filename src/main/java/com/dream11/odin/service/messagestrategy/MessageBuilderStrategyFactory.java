package com.dream11.odin.service.messagestrategy;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.constant.Action;
import com.dream11.odin.error.OdinError;
import java.util.Objects;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MessageBuilderStrategyFactory {

  private static ValidateMessageBuilderStrategy validateMessageBuilderStrategy;
  private static DeployMessageBuilderStrategy deployMessageBuilderStrategy;

  public static MessageBuilderStrategy getMessageBuilderStrategy(Action serviceAction) {
    switch (serviceAction) {
      case VALIDATE:
        {
          if (Objects.isNull(validateMessageBuilderStrategy))
            validateMessageBuilderStrategy = new ValidateMessageBuilderStrategy();
          return validateMessageBuilderStrategy;
        }
      case DEPLOY:
        {
          if (Objects.isNull(deployMessageBuilderStrategy))
            deployMessageBuilderStrategy = new DeployMessageBuilderStrategy();
          return deployMessageBuilderStrategy;
        }
      default:
        throw ExceptionUtil.getException(OdinError.INVALID_ACTION, serviceAction);
    }
  }
}
