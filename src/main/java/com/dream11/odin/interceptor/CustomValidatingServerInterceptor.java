package com.dream11.odin.interceptor;

import io.envoyproxy.pgv.ReflectiveValidatorIndex;
import io.envoyproxy.pgv.grpc.ValidatingServerInterceptor;

public class CustomValidatingServerInterceptor extends ValidatingServerInterceptor {

  public CustomValidatingServerInterceptor() {
    super(new ReflectiveValidatorIndex());
  }
}
