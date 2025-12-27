package com.dream11.odin.service.responseprocessor;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.dao.*;
import com.dream11.odin.dto.response.ResponseMessage;
import com.dream11.odin.dto.response.ResponseMessageType;
import com.dream11.odin.error.OdinError;
import com.google.inject.Inject;
import io.reactivex.Completable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ServiceResponseProcessor implements ResponseProcessor {
  final ServiceComponentDao serviceComponentDao;

  @Override
  public Completable process(ResponseMessage responseMessage) {
    log.info("Received message: {}", responseMessage.toString());
    if (!responseMessage.getData().isEmpty()) {
      return this.handleResponse(responseMessage);
    }
    return Completable.error(ExceptionUtil.getException(OdinError.INTERNAL_SERVER_ERROR));
  }

  private Completable handleResponse(ResponseMessage responseMessage) {
    if (responseMessage.getType().equals(ResponseMessageType.SERVICE_STATUS)) {
      return this.serviceComponentDao.updateEnvironmentServiceStatus(
          responseMessage.getStatus().getValue(), responseMessage.getId());
    } else if (responseMessage.getType().equals(ResponseMessageType.COMPONENT_STATUS)) {
      return this.serviceComponentDao.updateEnvironmentServiceComponentStatus(
          responseMessage.getStatus().getValue(),
          responseMessage.getId(),
          responseMessage.getData().get("componentName").toString());
    }
    return Completable.error(ExceptionUtil.getException(OdinError.INTERNAL_SERVER_ERROR));
  }
}
