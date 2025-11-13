package com.dream11.odin.responseautomata.impl;

import com.dream11.odin.responseautomata.ResponseAutomata;
import com.dream11.odin.responseautomata.ResponseState;
import com.dream11.odin.responseautomata.state.Deploy;
import com.dream11.odin.responseautomata.state.Validate;

public class ServiceResponseAutomata implements ResponseAutomata {
  private ResponseState currentState;
  private final ResponseState finalState;

  public ServiceResponseAutomata() {
    this.finalState = new Deploy(true, true);
    this.currentState = new Validate(false, true, this.finalState);
  }

  public ServiceResponseAutomata(ResponseState initialState, ResponseState finalState) {
    this.finalState = finalState;
    this.currentState = initialState;
  }

  @Override
  public <T> ResponseAutomata switchState(T response) {
    currentState = currentState.transition(response);
    return this;
  }

  @Override
  public boolean isComplete() {
    return currentState.getClass().equals(finalState.getClass()) && currentState.isComplete();
  }
}
