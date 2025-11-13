package com.dream11.odin.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.rpc.Code;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ErrorMapperUtilTest {

  private static final Map<Code, Response.Status> expectedMappings = ErrorMapperUtil.grpcToHttpMap;

  @Test
  void testGrpcToHttpStatusMappings() {
    for (Map.Entry<Code, Response.Status> entry : expectedMappings.entrySet()) {
      Response.Status actualStatus = ErrorMapperUtil.convertGrpcCodeToHttp(entry.getKey());
      assertThat(actualStatus)
          .as("Mismatch for gRPC Code: " + entry.getKey())
          .isEqualTo(entry.getValue());
    }
  }

  @Test
  void testDefaultMappingForUnknownCode() {
    Response.Status status = ErrorMapperUtil.convertGrpcCodeToHttp(null);
    assertThat(status)
        .as("Null input should return 500")
        .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR);
  }
}
