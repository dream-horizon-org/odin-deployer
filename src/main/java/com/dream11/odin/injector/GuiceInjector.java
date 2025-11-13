package com.dream11.odin.injector;

import com.google.inject.Injector;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class GuiceInjector
    implements com.dream11.grpc.ClassInjector, com.dream11.rest.ClassInjector {
  final Injector injector;

  @Override
  public <T> T getInstance(Class<T> clazz) {
    return this.injector.getInstance(clazz);
  }
}
