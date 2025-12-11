package com.dream11.odin.util;

import com.dream11.grpc.error.GrpcException;
import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.ApplicationContext;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dao.EnvironmentDao;
import com.dream11.odin.dto.ComponentData;
import com.dream11.odin.dto.ComponentId;
import com.dream11.odin.dto.ServiceData;
import com.dream11.odin.dto.UserDetails;
import com.dream11.odin.dto.v1.AccountInformation;
import com.dream11.odin.dto.v1.ProvisioningConfig;
import com.dream11.odin.dto.v1.ServiceDefinition;
import com.dream11.odin.entity.ComponentValidateTaskEntity;
import com.dream11.odin.entity.ServiceValidateTaskEntity;
import com.dream11.odin.error.OdinError;
import com.dream11.odin.grpc.service.ServiceStatus;
import com.dream11.odin.validations.*;
import io.reactivex.Completable;
import io.reactivex.exceptions.CompositeException;
import io.vertx.core.json.JsonObject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.commons.codec.digest.DigestUtils;

@UtilityClass
public class ValidationUtil {
  public Completable validateService(ServiceData serviceData) {
    Validator validator = new Validator();
    validator.add(
        Arrays.asList(
            new ServiceDefinitionValidator(serviceData.getServiceDefinition()),
            new ProvisioningConfigValidator(serviceData.getComponentProvisioningConfigs()),
            new ComponentProvisioningMappingValidator(
                serviceData.getServiceDefinition(), serviceData.getComponentProvisioningConfigs()),
            new ComponentDependsOnValidator(serviceData.getServiceDefinition())));

    return validator.validateAll();
  }

  public Completable validateEnvState(
      EnvironmentDao environmentDao, String envName, UserDetails userDetails) {
    Validator validator = new Validator();
    validator.add(new EnvironmentRunningValidator(environmentDao, envName, userDetails));

    return validator.validateAll();
  }

  public Completable validateDeploymentTypePrefix(
      ProvisioningConfig provisioningConfig,
      List<AccountInformation> accountInformationList,
      String envName) {
    Validator validator = new Validator();
    validator.add(
        new ComponentDeploymentTypeValidator(provisioningConfig, accountInformationList, envName));

    return validator.validateAll();
  }

  public ServiceValidateTaskEntity createServiceValidateTaskEntity(
      ServiceData serviceData, UserDetails userDetails) {
    JsonObject config = ServiceUtil.getServiceDefinitionConfig(serviceData.getServiceDefinition());
    return ServiceValidateTaskEntity.builder()
        .config(config)
        .serviceConfigHash(
            DigestUtils.sha256Hex(
                ServiceUtil.createServiceProvisioningConfigJson(serviceData).encode()))
        .name(serviceData.getServiceDefinition().getName())
        .serviceVersion(serviceData.getServiceDefinition().getVersion())
        .status(TaskStatus.IN_PROGRESS)
        .version(1)
        .traceId(ApplicationContext.getTraceId())
        .createdBy(userDetails.getUserId())
        .updatedBy(userDetails.getUserId())
        .build();
  }

  public List<ComponentValidateTaskEntity> createComponentValidateTaskEntities(
      ServiceDefinition serviceDefinition,
      Map<ComponentId, ComponentData> componentDataMap,
      ServiceValidateTaskEntity serviceValidateTaskEntity,
      UserDetails userDetails) {
    return serviceDefinition.getComponentsList().stream()
        .filter(
            component ->
                componentDataMap.containsKey(
                    ComponentUtil.buildComponentId(component.getName(), Action.VALIDATE)))
        .map(
            component ->
                createComponentValidateTaskEntity(
                    componentDataMap.get(
                        ComponentUtil.buildComponentId(component.getName(), Action.VALIDATE)),
                    serviceValidateTaskEntity,
                    userDetails))
        .toList();
  }

  @SneakyThrows
  public ComponentValidateTaskEntity createComponentValidateTaskEntity(
      ComponentData componentData,
      ServiceValidateTaskEntity serviceValidateTaskEntity,
      UserDetails userDetails) {
    JsonObject componentConfig =
        ComponentUtil.componentConfigToJson(
            componentData.getComponentDefinition(),
            componentData.getComponentProvisioningConfig(),
            componentData.getOperationConfigJson());
    return ComponentValidateTaskEntity.builder()
        .accounts(JsonUtil.getJsonFromProto(componentData.getEnvironmentProviderAccounts()))
        .serviceValidateTaskEntity(serviceValidateTaskEntity)
        .componentName(componentData.getComponentDefinition().getName())
        .status(TaskStatus.IN_PROGRESS)
        .config(componentConfig)
        .configHash(DigestUtils.sha256Hex(componentConfig.encode()))
        .version(1)
        .createdBy(userDetails.getUserId())
        .updatedBy(userDetails.getUserId())
        .build();
  }

  public boolean isValidateSuccessful(ServiceStatus status) {
    return status.getServiceStatus().equals(TaskStatus.SUCCESSFUL.getValue());
  }

  public GrpcException buildGrpcFromCompositeException(CompositeException ce) {
    String exceptionMessage =
        ce.getExceptions().stream().map(Throwable::getMessage).collect(Collectors.joining(", "));
    return ExceptionUtil.getException(OdinError.MULTIPLE_VALIDATION_EXCEPTION, exceptionMessage);
  }
}
