package com.dream11.odin.rest;

import com.dream11.odin.dto.response.StatusResponse;
import com.dream11.odin.service.ServiceBusiness;
import com.google.inject.Inject;
import hu.akarnokd.rxjava3.bridge.RxJavaBridge;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Path("/v1/operate/status/{serviceTaskId}")
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@ApiResponse(content = @Content(schema = @Schema(implementation = String.class)))
public class Status {

  private final ServiceBusiness serviceBusiness;

  @GET
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiResponse(content = @Content(schema = @Schema(implementation = String.class)))
  public CompletionStage<StatusResponse> handle(@PathParam("serviceTaskId") String serviceTaskId) {
    return RxJavaBridge.toV3Single(serviceBusiness.getServiceTaskStatus(serviceTaskId))
        .toCompletionStage();
  }
}
