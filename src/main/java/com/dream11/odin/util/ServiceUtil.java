package com.dream11.odin.util;

import com.dream11.odin.ApplicationContext;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dto.ComponentData;
import com.dream11.odin.dto.ServiceData;
import com.dream11.odin.dto.UserDetails;
import com.dream11.odin.dto.constants.RequestMessageType;
import com.dream11.odin.dto.requestqueue.ComponentAction;
import com.dream11.odin.dto.requestqueue.ServiceRequestMessageBody;
import com.dream11.odin.dto.requestqueue.ServiceRequestQueueMessage;
import com.dream11.odin.dto.v1.Environment;
import com.dream11.odin.dto.v1.ServiceDefinition;
import com.dream11.odin.entity.ServiceTaskEntity;
import com.dream11.odin.grpc.service.ServiceResponse;
import com.dream11.odin.injector.GuiceInjector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;

@Slf4j
@UtilityClass
public class ServiceUtil {

  /***
   * Calculates sha256 digest of combination of service definition and provisioning config
   * @param serviceData serviceData
   * @return sha256 digest
   */
  public String calculateServiceProvisioningHash(ServiceData serviceData) {
    StringBuilder stringBuilder = new StringBuilder();
    return DigestUtils.sha256Hex(
        stringBuilder
            .append(serviceData.getServiceDefinition().toString())
            .append(serviceData.getComponentProvisioningConfigs().toString())
            .toString());
  }

  /***
   * Creates single Json object containing service definition and provisioning config.
   * {
   *   "definitionConfig": {} -> service definition
   *   "provisioningConfig": [] -> provisioning config
   * }
   * @param serviceData serviceData
   * @return json object
   */
  public JsonObject createServiceProvisioningConfigJson(ServiceData serviceData) {
    JsonObject serviceDefinition =
        serviceDefinitionToJsonObject(serviceData.getServiceDefinition());
    JsonArray componentProvisioningConfigs =
        ComponentUtil.componentProvisioningConfigsToJsonArray(
            serviceData.getComponentProvisioningConfigs());

    JsonObject serviceProvisioningConfig =
        new JsonObject(
            Map.of(
                Constants.DEFINITION_CONFIG_KEY,
                serviceDefinition,
                Constants.PROVISIONING_CONFIG_KEY,
                componentProvisioningConfigs));

    return JsonUtil.sortJsonObject(serviceProvisioningConfig);
  }

  public ServiceRequestQueueMessage createPayload(
      String serviceName,
      String environmentName,
      List<ComponentAction> componentActions,
      Long serviceId,
      Long orgId) {

    return ServiceRequestQueueMessage.builder()
        .id(serviceId)
        .type(RequestMessageType.SERVICE)
        .body(
            ServiceRequestMessageBody.builder()
                .serviceName(serviceName)
                .environmentName(environmentName)
                .componentActions(componentActions)
                .orgId(orgId)
                .build())
        .traceId(ApplicationContext.getTraceId())
        .build();
  }

  public ServiceTaskEntity createServiceTaskEntity(
      ServiceData serviceData,
      Environment environment,
      Action actions,
      UserDetails userDetails,
      int prevServiceTaskEntityVersion) {
    return ServiceTaskEntity.builder()
        .actions(actions)
        .serviceConfigHash(
            DigestUtils.sha256Hex(createServiceProvisioningConfigJson(serviceData).encode()))
        .envId(environment.getId())
        .name(serviceData.getServiceDefinition().getName())
        .status(TaskStatus.IN_PROGRESS)
        .traceId(ApplicationContext.getTraceId())
        .version(prevServiceTaskEntityVersion + 1)
        .createdBy(userDetails.getUserId())
        .updatedBy(userDetails.getUserId())
        .build();
  }

  public JsonObject readServiceDefinitionSchema() throws IOException {
    return readSchema("service");
  }

  public JsonObject readProvisioningConfigSchema() throws IOException {
    return readSchema("provisioning");
  }

  public JsonObject readSchema(String source) throws IOException {
    String filePath =
        String.format(
            "%s/schema/%s/schema.json",
            Optional.ofNullable(System.getenv("RESOURCES_PATH")).orElse("src/main/resources"),
            source);
    return JsonObject.mapFrom(
        SharedDataUtil.getInstance(GuiceInjector.class)
            .getInstance(ObjectMapper.class)
            .readTree(new FileReader(filePath)));
  }

  public boolean isDeployInProgress(ServiceTaskEntity serviceTaskEntity) {
    return serviceTaskEntity.getActions().equals(Action.DEPLOY)
        && serviceTaskEntity.getStatus().equals(TaskStatus.IN_PROGRESS);
  }

  public JsonObject serviceDefinitionToJsonObject(ServiceDefinition serviceDefinition) {
    try {
      return new JsonObject(
          JsonFormat.printer().preservingProtoFieldNames().print(serviceDefinition));
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException("Invalid service definition schema format {}", e.getCause());
    }
  }

  public boolean isOdinConfigModified(
      ComponentData oldComponentData, ComponentData newComponentData) {
    return !oldComponentData
            .getComponentDefinition()
            .getType()
            .equals(newComponentData.getComponentDefinition().getType())
        || !oldComponentData
            .getComponentDefinition()
            .getVersion()
            .equals(newComponentData.getComponentDefinition().getVersion())
        || !oldComponentData
            .getComponentProvisioningConfig()
            .getDeploymentType()
            .equals(newComponentData.getComponentProvisioningConfig().getDeploymentType());
  }

  public boolean isConfigModified(ComponentData oldComponentData, ComponentData newComponentData) {
    return isOdinConfigModified(oldComponentData, newComponentData)
        || !oldComponentData
            .getComponentDefinition()
            .getConfig()
            .equals(newComponentData.getComponentDefinition().getConfig())
        || !oldComponentData
            .getComponentProvisioningConfig()
            .getParams()
            .equals(newComponentData.getComponentProvisioningConfig().getParams());
  }

  public String getNextServiceRevision(String serviceVersion) {
    // Bump revision number if service version contains -OPERATE
    if (serviceVersion.contains("-OPERATE.")) {
      String currentRevision = serviceVersion.split("-OPERATE\\.")[1];
      return String.format(
          "%s-OPERATE.%d",
          serviceVersion.split("-OPERATE\\.")[0], Integer.parseInt(currentRevision) + 1);
    }
    // Else add -OPERATE.1 to service version
    return String.format("%s-OPERATE.1", serviceVersion);
  }

  public boolean isUndeployInProgressOrSuccessful(ServiceResponse serviceResponse) {
    return Action.UNDEPLOY.getName().equals(serviceResponse.getServiceStatus().getServiceAction())
        && (TaskStatus.IN_PROGRESS
                .getValue()
                .equals(serviceResponse.getServiceStatus().getServiceStatus())
            || TaskStatus.SUCCESSFUL
                .getValue()
                .equals(serviceResponse.getServiceStatus().getServiceStatus()));
  }

  public boolean isOperateInProgress(ServiceTaskEntity serviceTaskEntity) {
    return Action.OPERATE.getName().equals(serviceTaskEntity.getActions().getName())
        && (TaskStatus.IN_PROGRESS.equals(serviceTaskEntity.getStatus()));
  }
}
