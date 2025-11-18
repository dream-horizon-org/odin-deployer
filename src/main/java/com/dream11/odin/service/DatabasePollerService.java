package com.dream11.odin.service;

import com.dream11.odin.config.AppConfig;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dao.ServiceComponentDao;
import com.dream11.odin.dao.ServiceTaskDao;
import com.dream11.odin.dao.ServiceValidateTaskDao;
import com.dream11.odin.dto.ServiceComponentValidateTaskStatus;
import com.dream11.odin.grpc.service.ComponentStatus;
import com.dream11.odin.grpc.service.ServiceResponse;
import com.dream11.odin.grpc.service.ServiceStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.reactivex.Flowable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class DatabasePollerService {
  public static final String ERROR_KEY = "error";
  final AppConfig appConfig;
  final ServiceTaskDao serviceTaskDao;
  final ServiceValidateTaskDao serviceValidateTaskDao;

  final ServiceComponentDao serviceComponentDao;

  public Flowable<ServiceResponse> pollDatabase(Long taskId, Action action) {
    return pollDatabase(taskId, action, null);
  }

  public Flowable<ServiceResponse> pollDatabase(
      Long taskId, Action action, Set<String> components) {
    return Flowable.interval(appConfig.getServiceDbStatusCheckIntervalSecs(), TimeUnit.SECONDS)
        .flatMap(
            tick ->
                (action.equals(Action.VALIDATE))
                    ? checkValidateStatusUpdate(taskId, components)
                    : checkStatusUpdate(taskId, components))
        .takeUntil(this::doTerminateDeploy);
  }

  public Flowable<ServiceResponse> pollDatabase(String serviceName, String envName, long orgId) {
    return Flowable.interval(appConfig.getServiceDbStatusCheckIntervalSecs(), TimeUnit.SECONDS)
        .flatMap(tick -> checkStatusUpdate(serviceName, envName, orgId))
        .takeUntil(this::doTerminateDeploy);
  }

  private Flowable<ServiceResponse> checkStatusUpdate(
      String serviceName, String envName, long orgId) {
    return serviceComponentDao
        .getServiceComponentStateInEnv(orgId, envName, serviceName)
        .map(
            environmentServiceComponentEntity ->
                ServiceResponse.newBuilder()
                    .setName(
                        environmentServiceComponentEntity
                            .getEnvironmentServiceEntity()
                            .getServiceName())
                    .setServiceStatus(
                        ServiceStatus.newBuilder()
                            .setServiceAction(
                                environmentServiceComponentEntity
                                    .getEnvironmentServiceEntity()
                                    .getServiceAction()
                                    .getName())
                            .setServiceStatus(
                                environmentServiceComponentEntity
                                    .getEnvironmentServiceEntity()
                                    .getServiceStatus()
                                    .getValue())
                            .build())
                    .addAllComponentsStatus(
                        environmentServiceComponentEntity.getComponents().stream()
                            .map(
                                componentEntity ->
                                    ComponentStatus.newBuilder()
                                        .setComponentName(componentEntity.getName())
                                        .setComponentAction(componentEntity.getAction().getName())
                                        .setComponentStatus(componentEntity.getStatus().getValue())
                                        .build())
                            .toList())
                    .build())
        .toFlowable();
  }

  private Flowable<ServiceResponse> checkStatusUpdate(Long serviceTaskId, Set<String> components) {
    return serviceTaskDao
        .getServiceComponentTaskStatusById(serviceTaskId)
        .map(
            serviceComponentTaskStatuses -> {
              boolean isInProgress =
                  serviceComponentTaskStatuses.stream()
                      .filter(
                          status ->
                              components == null || components.contains(status.componentName()))
                      .anyMatch(
                          status -> status.componentTaskStatus().equals(TaskStatus.IN_PROGRESS));

              ServiceStatus serviceStatus =
                  ServiceStatus.newBuilder()
                      .setServiceAction(
                          serviceComponentTaskStatuses.get(0).serviceTaskAction().getName())
                      .setServiceStatus(
                          isInProgress
                              ? TaskStatus.IN_PROGRESS.getValue()
                              : serviceComponentTaskStatuses.get(0).serviceTaskStatus().getValue())
                      .build();

              List<ComponentStatus> componentStatuses =
                  serviceComponentTaskStatuses.stream()
                      .filter(
                          status ->
                              components == null || components.contains(status.componentName()))
                      .map(
                          status ->
                              ComponentStatus.newBuilder()
                                  .setComponentName(status.componentName())
                                  .setComponentStatus(status.componentTaskStatus().getValue())
                                  .setComponentAction(status.componentTaskAction().getName())
                                  .setError(
                                      status.componentTaskResponse() != null
                                              && status
                                                  .componentTaskResponse()
                                                  .containsKey(ERROR_KEY)
                                          ? status.componentTaskResponse().getString(ERROR_KEY)
                                          : "")
                                  .build())
                      .toList();

              return ServiceResponse.newBuilder()
                  .setName(serviceComponentTaskStatuses.get(0).serviceName())
                  .setServiceStatus(serviceStatus)
                  .addAllComponentsStatus(componentStatuses)
                  .build();
            })
        .toFlowable();
  }

  private Flowable<ServiceResponse> checkValidateStatusUpdate(
      Long serviceTaskId, Set<String> components) {
    return serviceValidateTaskDao
        .getServiceComponentValidateTaskStatusById(serviceTaskId)
        .map(
            serviceComponentValidateTaskStatuses -> {
              boolean isInProgress =
                  serviceComponentValidateTaskStatuses.stream()
                      .filter(
                          status ->
                              components == null || components.contains(status.componentName()))
                      .anyMatch(
                          status ->
                              status.componentValidateTaskStatus().equals(TaskStatus.IN_PROGRESS));

              ServiceStatus serviceStatus =
                  ServiceStatus.newBuilder()
                      .setServiceAction(Action.VALIDATE.getName())
                      .setServiceStatus(
                          isInProgress
                              ? TaskStatus.IN_PROGRESS.getValue()
                              : serviceComponentValidateTaskStatuses
                                  .get(0)
                                  .serviceValidateTaskStatus()
                                  .getValue())
                      .build();

              List<ComponentStatus> componentStatuses =
                  serviceComponentValidateTaskStatuses.stream()
                      .filter(
                          status ->
                              components == null || components.contains(status.componentName()))
                      .map(
                          status ->
                              ComponentStatus.newBuilder()
                                  .setComponentName(status.componentName())
                                  .setComponentStatus(
                                      status.componentValidateTaskStatus().getValue())
                                  .setComponentAction(Action.VALIDATE.getName())
                                  .setError(getErrorString(status))
                                  .build())
                      .toList();

              return ServiceResponse.newBuilder()
                  .setName(serviceComponentValidateTaskStatuses.get(0).serviceName())
                  .setServiceStatus(serviceStatus)
                  .addAllComponentsStatus(componentStatuses)
                  .build();
            })
        .toFlowable();
  }

  @SneakyThrows
  private String getErrorString(ServiceComponentValidateTaskStatus serviceComponentTaskStatus) {
    ObjectMapper objectMapper = new ObjectMapper();
    return serviceComponentTaskStatus.componentValidateTaskResponse() == null
            || !serviceComponentTaskStatus.componentValidateTaskResponse().containsKey(ERROR_KEY)
        ? ""
        : objectMapper.writeValueAsString(
            serviceComponentTaskStatus.componentValidateTaskResponse().get(ERROR_KEY));
  }

  /*
   Terminate when :
       Any service action status is FAILED OR SUCCESSFUL
  */
  private boolean doTerminateDeploy(ServiceResponse serviceResponse) {
    return !serviceResponse.getMessageBytes().isEmpty()
        || serviceResponse
            .getServiceStatus()
            .getServiceStatus()
            .equals(TaskStatus.FAILED.getValue())
        || serviceResponse
            .getServiceStatus()
            .getServiceStatus()
            .equals(TaskStatus.SUCCESSFUL.getValue());
  }
}
