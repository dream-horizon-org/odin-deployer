package com.dream11.odin.rest;

import com.dream11.odin.ApplicationContext;
import com.dream11.odin.dto.request.OperateRequest;
import com.dream11.odin.grpc.service.OperateServiceRequest;
import com.dream11.odin.service.ServiceBusiness;
import com.dream11.odin.util.ApplicationUtil;
import com.dream11.rest.annotation.Timeout;
import com.google.inject.Inject;
import hu.akarnokd.rxjava3.bridge.RxJavaBridge;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Path("/v1/env/{environmentName}/service/{serviceName}/component/{componentName}/operate")
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class Operate {
  final ServiceBusiness serviceBusiness;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Timeout(300000)
  @ApiResponse(content = @Content(schema = @Schema(implementation = String.class)))
  public CompletionStage<Response> operate(
      @Valid OperateRequest operateRequest,
      @NotBlank @PathParam("environmentName") String environmentName,
      @NotBlank @PathParam("serviceName") String serviceName,
      @NotBlank @PathParam("componentName") String componentName) {
    String traceId = UUID.randomUUID().toString();
    ApplicationContext.setTraceId(traceId);

    OperateServiceRequest req =
        OperateServiceRequest.newBuilder()
            .setEnvName(environmentName)
            .setComponentName(componentName)
            .setServiceName(serviceName)
            .setIsComponentOperation(true)
            .setOperation(operateRequest.getOperationName())
            .setConfig(
                ApplicationUtil.toGrpcStruct(
                    new JsonObject(Json.encode(operateRequest.getConfig()))))
            .build();

    return RxJavaBridge.toV3Single(serviceBusiness.operateServiceFromRestEndpoint(req, traceId))
        .map(operateResponse -> Response.accepted(operateResponse).build())
        .toCompletionStage();
  }
}
