package com.dream11.odin.dto.response;

import com.dream11.odin.constant.TaskStatus;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
@AllArgsConstructor
@Getter
@ToString
@Builder
public class ResponseMessage {
  @NotNull Long id;
  @NotNull ResponseMessageType type;
  @NotBlank String executionId;
  @NotNull TaskStatus status;
  String error;

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = NamespaceResponseData.class, name = "NAMESPACE"),
    @JsonSubTypes.Type(value = ServiceResponseData.class, name = "COMPONENT_STATUS"),
    @JsonSubTypes.Type(value = ServiceResponseData.class, name = "SERVICE_STATUS")
  })
  @NotNull
  @Valid
  ResponseData data;
}
