package com.dream11.odin.util;

import com.google.rpc.Code;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class ErrorMapperUtil {

  public static final Map<Code, Response.Status> grpcToHttpMap =
      Map.ofEntries(
          Map.entry(Code.OK, Response.Status.OK),
          Map.entry(Code.UNKNOWN, Response.Status.INTERNAL_SERVER_ERROR),
          Map.entry(Code.INVALID_ARGUMENT, Response.Status.BAD_REQUEST),
          Map.entry(Code.DEADLINE_EXCEEDED, Response.Status.GATEWAY_TIMEOUT),
          Map.entry(Code.NOT_FOUND, Response.Status.NOT_FOUND),
          Map.entry(Code.ALREADY_EXISTS, Response.Status.CONFLICT),
          Map.entry(Code.PERMISSION_DENIED, Response.Status.FORBIDDEN),
          Map.entry(Code.UNAUTHENTICATED, Response.Status.UNAUTHORIZED),
          Map.entry(Code.RESOURCE_EXHAUSTED, Response.Status.TOO_MANY_REQUESTS),
          Map.entry(Code.FAILED_PRECONDITION, Response.Status.BAD_REQUEST),
          Map.entry(Code.ABORTED, Response.Status.CONFLICT),
          Map.entry(Code.OUT_OF_RANGE, Response.Status.BAD_REQUEST),
          Map.entry(Code.UNIMPLEMENTED, Response.Status.NOT_IMPLEMENTED),
          Map.entry(Code.INTERNAL, Response.Status.INTERNAL_SERVER_ERROR),
          Map.entry(Code.UNAVAILABLE, Response.Status.SERVICE_UNAVAILABLE),
          Map.entry(Code.DATA_LOSS, Response.Status.INTERNAL_SERVER_ERROR),
          Map.entry(Code.UNRECOGNIZED, Response.Status.INTERNAL_SERVER_ERROR));

  // Converts a gRPC code (integer) to an HTTP Response.Status.
  public Response.Status convertGrpcCodeToHttp(Code grpcCode) {
    if (grpcCode == null) {
      return Response.Status.INTERNAL_SERVER_ERROR;
    }
    return grpcToHttpMap.getOrDefault(grpcCode, Response.Status.INTERNAL_SERVER_ERROR);
  }
}
