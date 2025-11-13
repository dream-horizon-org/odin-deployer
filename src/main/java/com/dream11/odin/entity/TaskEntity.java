package com.dream11.odin.entity;

import static com.dream11.odin.constant.Constants.CREATED_AT;
import static com.dream11.odin.constant.Constants.UPDATED_AT;

import com.dream11.odin.constant.TaskStatus;
import com.fasterxml.jackson.annotation.JsonAlias;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
public abstract class TaskEntity {
  Long id;
  JsonObject config;

  TaskStatus status;
  Integer version;

  @JsonAlias({"createdBy", "created_by"})
  String createdBy;

  @JsonAlias({"createdAt", CREATED_AT})
  LocalDateTime createdAt;

  @JsonAlias({"updatedBy", "updated_by"})
  String updatedBy;

  @JsonAlias({"updatedAt", UPDATED_AT})
  LocalDateTime updatedAt;
}
