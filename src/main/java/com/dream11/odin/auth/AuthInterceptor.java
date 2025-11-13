package com.dream11.odin.auth;

import static com.dream11.odin.constant.Constants.AUTH_SKIPPED_SERVICES;

import com.dream11.grpc.annotation.GrpcInterceptor;
import com.dream11.odin.ApplicationContext;
import com.dream11.odin.dto.UserDetails;
import com.dream11.odin.error.OdinError;
import com.google.inject.Inject;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@GrpcInterceptor
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AuthInterceptor implements ServerInterceptor {
  static final Metadata.Key<String> AUTHORIZATION =
      Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
  private final JwtService jwtService;

  /*
  Generic name R1 refers to Request Type
               R2 refers to Response Type
   */
  @Override
  public <R1, R2> ServerCall.Listener<R1> interceptCall(
      ServerCall<R1, R2> serverCall, Metadata metadata, ServerCallHandler<R1, R2> next) {

    if (AUTH_SKIPPED_SERVICES.contains(serverCall.getMethodDescriptor().getFullMethodName())) {
      return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(
          next.startCall(serverCall, metadata)) {};
    }
    return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(
        next.startCall(serverCall, metadata)) {
      @Override
      public void onHalfClose() {
        String authToken = metadata.get(AUTHORIZATION);

        jwtService
            .verifyToken(authToken)
            .map(
                claims ->
                    UserDetails.builder()
                        .userId(claims.getSubject())
                        .orgId(claims.get("orgid", Integer.class).longValue())
                        .emailId(claims.getSubject())
                        .userToken(authToken)
                        .build())
            .doOnError(
                err -> {
                  log.error("Error while verifying user token {}", err.getMessage(), err);
                  serverCall.close(
                      Status.PERMISSION_DENIED.withDescription(
                          OdinError.INVALID_USER_LOGIN.getErrorMessage()),
                      new Metadata());
                })
            .subscribe(
                userDetails -> {
                  ApplicationContext.setUserDetails(userDetails);
                  super.onHalfClose();
                },
                err -> {});
      }
    };
  }
}
