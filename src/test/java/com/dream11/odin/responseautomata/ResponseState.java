package com.dream11.odin.responseautomata;

public interface ResponseState {
  <T> ResponseState transition(T response);

  boolean isComplete();
}
