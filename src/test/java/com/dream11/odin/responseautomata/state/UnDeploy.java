package com.dream11.odin.responseautomata.state;

import com.dream11.odin.constant.Action;
import com.dream11.odin.grpc.service.ServiceResponse;
import com.dream11.odin.responseautomata.ResponseState;
import com.dream11.odin.util.TestUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UnDeploy implements ResponseState {
  boolean isComplete = false;
  final boolean completable;
  final boolean onSuccess;

  public UnDeploy(boolean completable, boolean onSuccess) {
    this.completable = completable;
    this.onSuccess = onSuccess;
  }

  @Override
  public <T> ResponseState transition(T response) {
    ServiceResponse serviceResponse = (ServiceResponse) response;
    if (TestUtil.checkActionInProgress(serviceResponse, Action.UNDEPLOY)) {
      return this;
    } else if (TestUtil.checkActionSuccess(serviceResponse, Action.UNDEPLOY) && this.onSuccess) {
      this.isComplete = true;
      return this;
    } else if (TestUtil.checkActionFailed(serviceResponse, Action.UNDEPLOY) && !this.onSuccess) {
      this.isComplete = true;
      return this;
    }
    log.error("Invalid state from UnDeployInProgress. Response: {}", response);
    throw new IllegalStateException("Invalid state from UnDeployInProgress");
  }

  @Override
  public boolean isComplete() {
    return this.isComplete && this.completable;
  }
}
