package com.dream11.odin.validations;

import static org.assertj.core.api.Assertions.assertThat;

import com.dream11.grpc.error.GrpcException;
import com.dream11.odin.dto.v1.ComponentDefinition;
import com.dream11.odin.dto.v1.ServiceDefinition;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class ComponentDependsOnValidatorTest {

  @Test
  void shouldPassValidationWhenAllDependenciesArePresent(
      Vertx vertx, VertxTestContext vertxTestContext) {
    vertx.runOnContext(
        __ -> {
          // Create a service definition with components and dependencies
          List<ComponentDefinition> components =
              Arrays.asList(
                  ComponentDefinition.newBuilder()
                      .setName("Component1")
                      .addDependsOn("Component2")
                      .build(),
                  ComponentDefinition.newBuilder().setName("Component2").build());
          ServiceDefinition serviceDefinition =
              ServiceDefinition.newBuilder().addAllComponents(components).build();

          ComponentDependsOnValidator validator =
              new ComponentDependsOnValidator(serviceDefinition);

          validator.validate().subscribe(vertxTestContext::completeNow, vertxTestContext::failNow);
        });
  }

  @Test
  void shouldFailValidationWhenDependenciesAreMissing(
      Vertx vertx, VertxTestContext vertxTestContext) {
    vertx.runOnContext(
        __ -> {
          // Create a service definition with components and missing dependencies
          List<ComponentDefinition> components =
              Arrays.asList(
                  ComponentDefinition.newBuilder()
                      .setName("Component1")
                      .addDependsOn("Component2")
                      .build(),
                  ComponentDefinition.newBuilder().setName("Component2").build(),
                  ComponentDefinition.newBuilder()
                      .setName("Component3")
                      .addDependsOn("NonExistentComponent")
                      .build());
          ServiceDefinition serviceDefinition =
              ServiceDefinition.newBuilder().addAllComponents(components).build();

          ComponentDependsOnValidator validator =
              new ComponentDependsOnValidator(serviceDefinition);

          validator
              .validate()
              .subscribe(
                  () -> vertxTestContext.failNow("GrpcException should be thrown"),
                  err -> {
                    try {
                      assertThat(err).isInstanceOf(GrpcException.class);
                      vertxTestContext.completeNow();
                    } catch (Throwable e) {
                      vertxTestContext.failNow(e);
                    }
                  });
        });
  }
}
