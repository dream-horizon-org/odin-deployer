package com.dream11.odin.config;

import com.dream11.queue.impl.sqs.SqsConfig;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class QueueConfig {
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "queueProvider")
  @JsonSubTypes({@JsonSubTypes.Type(value = SqsConfig.class, name = "sqs")})
  @NotNull
  @Valid
  com.dream11.queue.config.QueueConfig request;

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "queueProvider")
  @JsonSubTypes({@JsonSubTypes.Type(value = SqsConfig.class, name = "sqs")})
  @NotNull
  @Valid
  com.dream11.queue.config.QueueConfig response;
}
