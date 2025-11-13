package com.dream11.odin.responseautomata.state;

import com.dream11.odin.constant.Action;
import com.dream11.odin.grpc.service.ServiceResponse;
import com.dream11.odin.responseautomata.ResponseState;
import com.dream11.odin.util.TestUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Validate implements ResponseState {
  boolean isComplete = false;
  final boolean completable;
  final boolean onSuccess;
  final ResponseState nextState;

  public Validate(boolean completable, boolean onSuccess, ResponseState nextState) {
    this.completable = completable;
    this.onSuccess = onSuccess;
    this.nextState = nextState;
  }

  public Validate(boolean onSuccess) {
    this.completable = true;
    this.onSuccess = onSuccess;
    this.nextState = this;
  }

  @Override
  public <T> ResponseState transition(T response) {
    ServiceResponse serviceResponse = (ServiceResponse) response;
    if (TestUtil.checkActionInProgress(serviceResponse, Action.VALIDATE)) {
      return this;
    } else if (TestUtil.checkActionSuccess(serviceResponse, Action.VALIDATE) && this.onSuccess) {
      this.isComplete = true;
      return this;
    } else if (TestUtil.checkActionFailed(serviceResponse, Action.VALIDATE) && !this.onSuccess) {
      this.isComplete = true;
      return this;
    }
    // Move to next state if the current state is complete and the response is in progress for next
    // state
    if (TestUtil.checkActionInProgress(serviceResponse, Action.DEPLOY)
        && this.onSuccess
        && this.nextState.getClass().equals(Deploy.class)) {
      return this.nextState;
    } else if (TestUtil.checkActionInProgress(serviceResponse, Action.OPERATE)
        && this.onSuccess
        && this.nextState.getClass().equals(Operate.class)) {
      return this.nextState;
    }
    log.error("Invalid state from ValidateInProgress. Response: {}", response);
    throw new IllegalStateException("Invalid state from ValidateInProgress");
  }

  @Override
  public boolean isComplete() {
    return this.isComplete && this.completable;
  }
}
