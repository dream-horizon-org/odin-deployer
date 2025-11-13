package com.dream11.odin.responseautomata.state;

import com.dream11.odin.grpc.environment.DeleteEnvironmentResponse;
import com.dream11.odin.responseautomata.ResponseState;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeleteEnvironment implements ResponseState {
  boolean isComplete = false;
  final boolean onSuccess;

  public DeleteEnvironment(boolean onSuccess) {
    this.onSuccess = onSuccess;
  }

  @Override
  public <T> ResponseState transition(T response) {
    DeleteEnvironmentResponse deleteEnvironmentResponse = (DeleteEnvironmentResponse) response;
    if (deleteEnvironmentResponse.getMessage().contains("IN_PROGRESS")) {
      return this;
    } else if (deleteEnvironmentResponse.getMessage().contains("DELETED") && this.onSuccess) {
      this.isComplete = true;
      return this;
    } else if (deleteEnvironmentResponse.getMessage().contains("FAILED") && !this.onSuccess) {
      this.isComplete = true;
      return this;
    }
    log.error("Invalid state from DeleteInProgress. Response: {}", response);
    throw new IllegalStateException("Invalid state from DeleteInProgress");
  }

  @Override
  public boolean isComplete() {
    return this.isComplete;
  }
}
