package com.dream11.odin.responseautomata.state;

import com.dream11.odin.constant.EnvironmentStatus;
import com.dream11.odin.grpc.environment.CreateEnvironmentResponse;
import com.dream11.odin.responseautomata.ResponseState;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CreateEnvironment implements ResponseState {
  boolean isComplete = false;
  final boolean onSuccess;

  public CreateEnvironment(boolean onSuccess) {
    this.onSuccess = onSuccess;
  }

  @Override
  public <T> ResponseState transition(T response) {
    CreateEnvironmentResponse createEnvironmentResponse = (CreateEnvironmentResponse) response;
    if (createEnvironmentResponse.getMessage().contains("IN_PROGRESS")) {
      return this;
    } else if (createEnvironmentResponse.getMessage().contains(EnvironmentStatus.RUNNING.name())
        && this.onSuccess) {
      this.isComplete = true;
      return this;
    } else if (createEnvironmentResponse.getMessage().contains("FAILED") && !this.onSuccess) {
      this.isComplete = true;
      return this;
    }
    log.error("Invalid state from CreateEnvInProgress. Response: {}", response);
    throw new IllegalStateException("Invalid state from CreateEnvInProgress");
  }

  @Override
  public boolean isComplete() {
    return this.isComplete;
  }
}
