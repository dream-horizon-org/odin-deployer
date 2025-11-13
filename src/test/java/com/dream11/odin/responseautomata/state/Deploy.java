package com.dream11.odin.responseautomata.state;

import com.dream11.odin.constant.Action;
import com.dream11.odin.grpc.service.ServiceResponse;
import com.dream11.odin.responseautomata.ResponseState;
import com.dream11.odin.util.TestUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Deploy implements ResponseState {
  boolean isComplete = false;
  final boolean completable;
  final boolean onSuccess;

  public Deploy(boolean completable, boolean onSuccess) {
    this.completable = completable;
    this.onSuccess = onSuccess;
  }

  @Override
  public <T> ResponseState transition(T response) {
    ServiceResponse serviceResponse = (ServiceResponse) response;
    if (TestUtil.checkActionInProgress(serviceResponse, Action.DEPLOY)) {
      return this;
    } else if (TestUtil.checkActionSuccess(serviceResponse, Action.DEPLOY) && this.onSuccess) {
      this.isComplete = true;
      return this;
    } else if (TestUtil.checkActionFailed(serviceResponse, Action.DEPLOY) && !this.onSuccess) {
      this.isComplete = true;
      return this;
    }
    log.error("Invalid state from DeployInProgress. Response: {}", response);
    throw new IllegalStateException("Invalid state from DeployInProgress");
  }

  @Override
  public boolean isComplete() {
    return this.isComplete && this.completable;
  }
}
