package com.dream11.odin.constant;

import lombok.Getter;

public enum TaskStatus {
  IN_PROGRESS("IN_PROGRESS"),
  SUCCESSFUL("SUCCESSFUL"),
  FAILED("FAILED");

  @Getter private final String value;

  TaskStatus(String value) {
    this.value = value;
  }
}
