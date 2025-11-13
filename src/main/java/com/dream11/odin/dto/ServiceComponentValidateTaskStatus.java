package com.dream11.odin.dto;

import com.dream11.odin.constant.TaskStatus;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.Map;
import lombok.Builder;

@Builder
public record ServiceComponentValidateTaskStatus(
    @JsonAlias({"serviceName", "service_name"}) String serviceName,
    @JsonAlias({"serviceVersion", "service_version"}) String serviceVersion,
    @JsonAlias({"serviceValidateTaskId", "service_validate_task_id"}) Integer serviceValidateTaskId,
    @JsonAlias({"serviceValidateTaskStatus", "service_validate_task_status"})
        TaskStatus serviceValidateTaskStatus,
    @JsonAlias({"componentValidateTaskId", "component_validate_task_id"})
        Integer componentValidateTaskId,
    @JsonAlias({"componentValidateTaskStatus", "component_validate_task_status"})
        TaskStatus componentValidateTaskStatus,
    @JsonAlias({"componentValidateTaskResponse", "component_validate_task_response"})
        Map<String, Object> componentValidateTaskResponse,
    @JsonAlias({"componentName", "component_name"}) String componentName) {}
