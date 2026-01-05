package com.dream11.odin.service.responseprocessor;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.dao.*;
import com.dream11.odin.dto.response.ResponseMessage;
import com.dream11.odin.dto.response.ResponseMessageType;
import com.dream11.odin.dto.response.ServiceResponseData;
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
    return this.handleResponse(responseMessage);
  }

  private Completable handleResponse(ResponseMessage responseMessage) {
    if (responseMessage.getType().equals(ResponseMessageType.SERVICE_STATUS)) {
      // TODO update execution task and lock release
      return this.serviceComponentDao.updateEnvironmentServiceStatus(
          responseMessage.getStatus().getValue(), responseMessage.getId());
    } else if (responseMessage.getType().equals(ResponseMessageType.COMPONENT_STATUS)) {
      return this.serviceComponentDao.updateEnvironmentServiceComponentStatus(
          responseMessage.getStatus().getValue(),
          responseMessage.getId(),
          ((ServiceResponseData) responseMessage.getData()).getComponentName());
    }
    return Completable.error(ExceptionUtil.getException(OdinError.INTERNAL_SERVER_ERROR));
  }
}
