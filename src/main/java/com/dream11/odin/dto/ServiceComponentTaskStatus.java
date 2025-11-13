package com.dream11.odin.dto;

import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.TaskStatus;
import com.fasterxml.jackson.annotation.JsonAlias;
import io.vertx.core.json.JsonObject;
import lombok.Builder;

@Builder
public record ServiceComponentTaskStatus(
    @JsonAlias({"serviceName", "service_name"}) String serviceName,
    @JsonAlias({"serviceVersion", "service_version"}) String serviceVersion,
    @JsonAlias({"serviceTaskId", "service_task_id"}) Integer serviceTaskId,
    @JsonAlias({"serviceTaskAction", "service_task_action"}) Action serviceTaskAction,
    @JsonAlias({"serviceTaskStatus", "service_task_status"}) TaskStatus serviceTaskStatus,
    @JsonAlias({"componentTaskId", "component_task_id"}) Integer componentTaskId,
    @JsonAlias({"componentTaskAction", "component_task_action"}) Action componentTaskAction,
    @JsonAlias({"componentTaskStatus", "component_task_status"}) TaskStatus componentTaskStatus,
    @JsonAlias({"componentTaskResponse", "component_task_response"})
        JsonObject componentTaskResponse,
    @JsonAlias({"componentConfig", "component_config"}) JsonObject componentConfig,
    @JsonAlias({"componentName", "component_name"}) String componentName) {}
