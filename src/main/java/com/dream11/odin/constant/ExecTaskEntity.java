package com.dream11.odin.constant;

import lombok.Getter;

public enum ExecTaskEntity {
  ENVIRONMENT("ENVIRONMENT"),
  SERVICE("SERVICE");

  @Getter private final String value;

  ExecTaskEntity(String value) {
    this.value = value;
  }
}
