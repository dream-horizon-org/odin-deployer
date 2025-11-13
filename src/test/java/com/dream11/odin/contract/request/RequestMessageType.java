package com.dream11.odin.contract.request;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum RequestMessageType {
  SERVICE("service"),
  NAMESPACE("namespace");
  final String name;
}
