package com.dream11.odin.interceptor;

import com.dream11.odin.constant.Constants;
import io.grpc.Metadata;
import io.vertx.grpc.ContextServerInterceptor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CustomContextServerInterceptor extends ContextServerInterceptor {
  private static final String TRACE_ID_HEADER = "trace-id";
  static final Metadata.Key<String> TRACE_ID =
      Metadata.Key.of(TRACE_ID_HEADER, Metadata.ASCII_STRING_MARSHALLER);

  @Override
  public void bind(Metadata metadata) {
    if (metadata.containsKey(TRACE_ID)) {
      log.info("Adding traceId to context: {}", metadata.get(TRACE_ID));
      ContextServerInterceptor.put(Constants.TRACE_ID, metadata.get(TRACE_ID));
    }
  }
}
