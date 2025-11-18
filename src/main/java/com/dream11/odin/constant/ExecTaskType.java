package com.dream11.odin.constant;

import lombok.Getter;

public enum ExecTaskType {
  ENVIRONMENT("ENVIRONMENT"),
  SERVICE("SERVICE");

  @Getter private final String value;

  ExecTaskType(String value) {
    this.value = value;
  }
}
