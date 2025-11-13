package com.dream11.odin.rest;

import com.google.inject.Inject;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.vertx.core.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Path("/healthcheck")
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@ApiResponse(content = @Content(schema = @Schema(implementation = String.class)))
public class Healthcheck {

  @GET
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiResponse(content = @Content(schema = @Schema(implementation = String.class)))
  public JsonObject handle() {
    return JsonObject.of("status", "RUNNING");
  }
}
