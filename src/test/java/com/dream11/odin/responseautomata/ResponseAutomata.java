package com.dream11.odin.responseautomata;

public interface ResponseAutomata {
  <T> ResponseAutomata switchState(T response);

  boolean isComplete();
}
