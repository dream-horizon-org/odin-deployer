package com.dream11.odin.entity;

import com.dream11.odin.constant.TaskStatus;
import java.util.Optional;
import lombok.Builder;

@Builder
public record EnvironmentTask(
    long id,
    long envId,
    TaskStatus status,
    long actionId,
    int version,
    String createdBy,
    String serviceAccountSnapshot,
    String providerAccountName,
    Optional<String> response,
    Optional<String> traceId) {}
