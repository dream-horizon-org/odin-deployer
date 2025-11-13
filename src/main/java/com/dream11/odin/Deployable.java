package com.dream11.odin;

import com.google.inject.Inject;
import io.vertx.core.DeploymentOptions;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class Deployable {
  final String verticle;
  final DeploymentOptions deploymentOptions;
}
