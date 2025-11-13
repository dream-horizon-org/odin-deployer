package com.dream11.odin.service.responseprocessor;

import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.dao.ComponentTaskDao;
import com.dream11.odin.dao.ComponentValidateTaskDao;
import com.dream11.odin.dao.ServiceTaskDao;
import com.dream11.odin.dao.ServiceValidateTaskDao;
import com.dream11.odin.dto.response.ResponseMessage;
import com.dream11.odin.dto.response.ResponseMessageType;
import com.dream11.odin.entity.ComponentTaskEntity;
import com.dream11.odin.entity.ComponentValidateTaskEntity;
import com.dream11.odin.entity.ServiceTaskEntity;
import com.dream11.odin.entity.ServiceValidateTaskEntity;
import com.google.inject.Inject;
import io.reactivex.Completable;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ServiceResponseProcessor implements ResponseProcessor {

  final ComponentTaskDao componentTaskDao;
  final ServiceTaskDao serviceTaskDao;
  final ComponentValidateTaskDao componentValidateTaskDao;
  final ServiceValidateTaskDao serviceValidateTaskDao;

  @Override
  public Completable process(ResponseMessage responseMessage) {
    log.info("Processing {}", responseMessage);
    if (responseMessage.getData() != null
        && !responseMessage.getData().isEmpty()
        && responseMessage.getData().containsKey(Constants.STAGE)
        && responseMessage
            .getData()
            .get(Constants.STAGE)
            .toString()
            .equalsIgnoreCase(Action.VALIDATE.getName())) {
      return handleValidateResponse(responseMessage);
    } else {
      return handleResponse(responseMessage);
    }
  }

  private Completable handleValidateResponse(ResponseMessage responseMessage) {
    if (responseMessage.getType().equals(ResponseMessageType.SERVICE_STATUS)) {
      return serviceValidateTaskDao
          .updateServiceValidateTaskByDeploymentId(
              ServiceValidateTaskEntity.builder()
                  .id(responseMessage.getId())
                  .status(responseMessage.getStatus())
                  .build())
          .doOnSuccess(
              serviceValidateTaskEntity ->
                  log.info(
                      "ServiceValidateTaskEntity Id:{} updated successfully",
                      serviceValidateTaskEntity.getId()))
          .ignoreElement();
    } else if (responseMessage.getType().equals(ResponseMessageType.COMPONENT_STATUS)) {
      return componentValidateTaskDao
          .updateComponentValidateTask(
              ComponentValidateTaskEntity.builder()
                  .status(responseMessage.getStatus())
                  .response(
                      StringUtils.isNotBlank(responseMessage.getError())
                          ? new JsonObject().put("error", responseMessage.getError())
                          : new JsonObject())
                  .serviceValidateTaskEntity(
                      ServiceValidateTaskEntity.builder().id(responseMessage.getId()).build())
                  .componentName(responseMessage.getData().get("componentName").toString())
                  .build())
          .ignoreElement();
    } else {
      return Completable.error(
          new IllegalArgumentException(
              "Invalid ResponseMessage type for validate: " + responseMessage));
    }
  }

  private Completable handleResponse(ResponseMessage responseMessage) {
    if (responseMessage.getType().equals(ResponseMessageType.SERVICE_STATUS)) {
      return serviceTaskDao
          .updateServiceTaskByDeploymentId(
              ServiceTaskEntity.builder()
                  .id(responseMessage.getId())
                  .status(responseMessage.getStatus())
                  .build())
          .ignoreElement();
    } else if (responseMessage.getType().equals(ResponseMessageType.COMPONENT_STATUS)) {
      return componentTaskDao
          .updateComponentTasks(
              ComponentTaskEntity.builder()
                  .status(responseMessage.getStatus())
                  .serviceTaskEntity(
                      ServiceTaskEntity.builder().id(responseMessage.getId()).build())
                  .componentName(responseMessage.getData().get("componentName").toString())
                  .response(
                      responseMessage.getError() != null && !responseMessage.getError().isEmpty()
                          ? new JsonObject().put("error", responseMessage.getError())
                          : new JsonObject())
                  .action(
                      Action.forAction(responseMessage.getData().get(Constants.STAGE).toString()))
                  .build())
          .ignoreElement();
    } else {
      return Completable.error(
          new IllegalArgumentException("Invalid ResponseMessage type: " + responseMessage));
    }
  }
}
