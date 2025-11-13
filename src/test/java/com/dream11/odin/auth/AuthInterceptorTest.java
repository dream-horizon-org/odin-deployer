package com.dream11.odin.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dream11.odin.MainModule;
import com.dream11.odin.injector.GuiceInjector;
import com.dream11.odin.util.SharedDataUtil;
import com.google.inject.Guice;
import com.google.protobuf.Empty;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.protobuf.ProtoUtils;
import io.jsonwebtoken.Claims;
import io.reactivex.Single;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, VertxExtension.class})
class AuthInterceptorTest {

  private AuthInterceptor authInterceptor;
  @Mock private JwtService jwtService;

  @Mock private Claims claims;

  @BeforeEach
  void setUp(Vertx vertx) {
    GuiceInjector injector =
        new GuiceInjector(Guice.createInjector(List.of(new MainModule(vertx))));
    SharedDataUtil.setInstance(vertx, injector);
    MockitoAnnotations.openMocks(this);
    authInterceptor = new AuthInterceptor(jwtService);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testInterceptCallSkippedService(Vertx vertx, VertxTestContext vertxTestContext) {
    vertx.runOnContext(
        __ -> {
          // Arrange
          ServerCallHandler<Empty, Empty> next = mock(ServerCallHandler.class);
          ServerCall<Empty, Empty> serverCall = mock(ServerCall.class);
          Metadata metadata = new Metadata();
          metadata.put(AuthInterceptor.AUTHORIZATION, "valid-token");
          MethodDescriptor<Empty, Empty> methodDescriptor =
              MethodDescriptor.<Empty, Empty>newBuilder()
                  .setType(MethodDescriptor.MethodType.UNARY)
                  .setFullMethodName("grpc.health.v1.Health/Check")
                  .setRequestMarshaller(ProtoUtils.marshaller(Empty.getDefaultInstance()))
                  .setResponseMarshaller(ProtoUtils.marshaller(Empty.getDefaultInstance()))
                  .build();

          when(serverCall.getMethodDescriptor()).thenReturn(methodDescriptor);

          // Act
          authInterceptor.interceptCall(serverCall, metadata, next);

          // Assert
          verify(next).startCall(serverCall, metadata);
          verify(serverCall, never()).close(ArgumentMatchers.any(), ArgumentMatchers.any());
          vertxTestContext.completeNow();
        });
  }

  @Test
  @SuppressWarnings("unchecked")
  void testInterceptCallForbidden(Vertx vertx, VertxTestContext vertxTestContext) {
    vertx.runOnContext(
        __ -> {
          // Arrange
          ServerCallHandler<Empty, Empty> next = mock(ServerCallHandler.class);
          ServerCall<Empty, Empty> serverCall = mock(ServerCall.class);
          doAnswer(
                  invocation -> {
                    vertxTestContext.completeNow();
                    return null;
                  })
              .when(serverCall)
              .close(any(), any());
          Metadata metadata = new Metadata();
          metadata.put(AuthInterceptor.AUTHORIZATION, "invalid-token");
          MethodDescriptor<Empty, Empty> methodDescriptor =
              MethodDescriptor.<Empty, Empty>newBuilder()
                  .setType(MethodDescriptor.MethodType.UNARY)
                  .setFullMethodName("anything")
                  .setRequestMarshaller(ProtoUtils.marshaller(Empty.getDefaultInstance()))
                  .setResponseMarshaller(ProtoUtils.marshaller(Empty.getDefaultInstance()))
                  .build();
          when(serverCall.getMethodDescriptor()).thenReturn(methodDescriptor);
          when(jwtService.verifyToken(anyString()))
              .thenReturn(Single.error(new RuntimeException("token verification failed")));

          // Act
          ServerCall.Listener<Empty> listener =
              authInterceptor.interceptCall(serverCall, metadata, next);
          listener.onHalfClose();

          // Assert
          verify(next).startCall(serverCall, metadata);
          verify(serverCall).close(ArgumentMatchers.any(), ArgumentMatchers.any());
        });
  }

  @Test
  @SuppressWarnings("unchecked")
  void testInterceptCallBadRequest(Vertx vertx, VertxTestContext vertxTestContext) {
    vertx.runOnContext(
        __ -> {
          // Arrange
          ServerCallHandler<Empty, Empty> next = mock(ServerCallHandler.class);
          ServerCall<Empty, Empty> serverCall = mock(ServerCall.class);
          doAnswer(
                  invocation -> {
                    vertxTestContext.completeNow();
                    return null;
                  })
              .when(serverCall)
              .close(any(), any());
          Metadata metadata = new Metadata();
          metadata.put(AuthInterceptor.AUTHORIZATION, "invalid-token");
          MethodDescriptor<Empty, Empty> methodDescriptor =
              MethodDescriptor.<Empty, Empty>newBuilder()
                  .setType(MethodDescriptor.MethodType.UNARY)
                  .setFullMethodName("anything")
                  .setRequestMarshaller(ProtoUtils.marshaller(Empty.getDefaultInstance()))
                  .setResponseMarshaller(ProtoUtils.marshaller(Empty.getDefaultInstance()))
                  .build();
          when(serverCall.getMethodDescriptor()).thenReturn(methodDescriptor);
          when(jwtService.verifyToken(anyString()))
              .thenReturn(Single.error(new RuntimeException("token verification failed")));

          // Act
          ServerCall.Listener<Empty> listener =
              authInterceptor.interceptCall(serverCall, metadata, next);
          listener.onHalfClose();

          // Assert
          verify(next).startCall(serverCall, metadata);
          verify(serverCall).close(ArgumentMatchers.any(), ArgumentMatchers.any());
        });
  }

  @Test
  @SuppressWarnings("unchecked")
  void testInterceptCallInternal(Vertx vertx, VertxTestContext vertxTestContext) {
    vertx.runOnContext(
        __ -> {
          // Arrange
          ServerCallHandler<Empty, Empty> next = mock(ServerCallHandler.class);
          ServerCall<Empty, Empty> serverCall = mock(ServerCall.class);
          doAnswer(
                  invocation -> {
                    vertxTestContext.completeNow();
                    return null;
                  })
              .when(serverCall)
              .close(any(), any());
          Metadata metadata = new Metadata();
          metadata.put(AuthInterceptor.AUTHORIZATION, "invalid-token");
          MethodDescriptor<Empty, Empty> methodDescriptor =
              MethodDescriptor.<Empty, Empty>newBuilder()
                  .setType(MethodDescriptor.MethodType.UNARY)
                  .setFullMethodName("anything")
                  .setRequestMarshaller(ProtoUtils.marshaller(Empty.getDefaultInstance()))
                  .setResponseMarshaller(ProtoUtils.marshaller(Empty.getDefaultInstance()))
                  .build();
          when(serverCall.getMethodDescriptor()).thenReturn(methodDescriptor);
          when(jwtService.verifyToken(anyString()))
              .thenReturn(Single.error(new RuntimeException("token verification failed")));

          // Act
          ServerCall.Listener<Empty> listener =
              authInterceptor.interceptCall(serverCall, metadata, next);
          listener.onHalfClose();

          // Assert
          verify(next).startCall(serverCall, metadata);
          verify(serverCall).close(ArgumentMatchers.any(), ArgumentMatchers.any());
        });
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testInterceptCallValidToken(Vertx vertx, VertxTestContext vertxTestContext) {
    vertx.runOnContext(
        __ -> {

          // Arrange
          ServerCall.Listener<Empty> delegateListener =
              new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(
                  mock(ServerCall.Listener.class)) {
                @Override
                public void onHalfClose() {
                  super.onHalfClose();
                  vertxTestContext.completeNow();
                }
              };

          ServerCallHandler<Empty, Empty> next = mock(ServerCallHandler.class);
          ServerCall<Empty, Empty> serverCall = mock(ServerCall.class);
          Metadata metadata = new Metadata();
          metadata.put(AuthInterceptor.AUTHORIZATION, "valid-token");
          MethodDescriptor<Empty, Empty> methodDescriptor =
              MethodDescriptor.<Empty, Empty>newBuilder()
                  .setType(MethodDescriptor.MethodType.UNARY)
                  .setFullMethodName("anything")
                  .setRequestMarshaller(ProtoUtils.marshaller(Empty.getDefaultInstance()))
                  .setResponseMarshaller(ProtoUtils.marshaller(Empty.getDefaultInstance()))
                  .build();
          when(serverCall.getMethodDescriptor()).thenReturn(methodDescriptor);
          when(next.startCall(serverCall, metadata)).thenReturn(delegateListener);
          when(jwtService.verifyToken(anyString())).thenReturn(Single.just(claims));
          when(claims.getSubject()).thenReturn("test-user");
          when(claims.get("orgid", Integer.class)).thenReturn(1);

          // Act
          ServerCall.Listener<Empty> listener =
              authInterceptor.interceptCall(serverCall, metadata, next);
          listener.onHalfClose();

          // Assert
          verify(next).startCall(serverCall, metadata);
          verify(serverCall, never()).close(ArgumentMatchers.any(), ArgumentMatchers.any());
        });
  }
}
