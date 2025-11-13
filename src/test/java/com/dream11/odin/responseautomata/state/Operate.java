package com.dream11.odin.responseautomata.state;

import com.dream11.odin.constant.Action;
import com.dream11.odin.grpc.service.ServiceResponse;
import com.dream11.odin.responseautomata.ResponseState;
import com.dream11.odin.util.TestUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Operate implements ResponseState {
  boolean isComplete = false;
  final boolean completable;
  final boolean onSuccess;

  public Operate(boolean completable, boolean onSuccess) {
    this.completable = completable;
    this.onSuccess = onSuccess;
  }

  public <T> ResponseState transition(T response) {
    ServiceResponse serviceResponse = (ServiceResponse) response;
    if (TestUtil.checkActionInProgress(serviceResponse, Action.OPERATE)) {
      return this;
    } else if (TestUtil.checkActionSuccess(serviceResponse, Action.OPERATE) && this.onSuccess) {
      this.isComplete = true;
      return this;
    } else if (TestUtil.checkActionFailed(serviceResponse, Action.OPERATE) && !this.onSuccess) {
      this.isComplete = true;
      return this;
    }
    log.error("Invalid state from OperateInProgress. Response: {}", response);
    throw new IllegalStateException("Invalid state from OperateInProgress");
  }

  public boolean isComplete() {
    return this.isComplete && this.completable;
  }
}
