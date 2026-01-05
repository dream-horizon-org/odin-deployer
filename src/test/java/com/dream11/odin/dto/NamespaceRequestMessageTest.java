package com.dream11.odin.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dream11.odin.constant.Action;
import com.dream11.odin.dto.constants.RequestMessageType;
import com.dream11.odin.dto.request.NamespaceRequestMessage;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountResponse;
import com.dream11.odin.oam.MockOAMProviderAccountService;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

class NamespaceRequestMessageTest {

  @Test
  void testMessage() {

    // Arrange
    String environmentName = "test";
    Long orgId = 1L;
    GetProviderAccountResponse providerAccountResponse =
        MockOAMProviderAccountService.accountWithClusterResponse;

    // Act
    JsonObject request =
        new NamespaceRequestMessage(
                environmentName,
                providerAccountResponse,
                Action.CREATE_ENVIRONMENT,
                1,
                RequestMessageType.NAMESPACE,
                orgId,
                "test-trace-id")
            .createRequest();

    // Assert
    assertEquals(environmentName, request.getJsonObject("body").getString("name"));
    assertEquals(orgId, request.getJsonObject("body").getLong("orgId"));
    assertEquals(
        Action.CREATE_ENVIRONMENT.getName(), request.getJsonObject("body").getString("action"));
  }
}
