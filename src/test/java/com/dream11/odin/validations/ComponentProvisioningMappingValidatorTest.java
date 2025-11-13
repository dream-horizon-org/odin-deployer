package com.dream11.odin.validations;

import static org.assertj.core.api.Assertions.assertThat;

import com.dream11.grpc.error.GrpcException;
import com.dream11.odin.dto.v1.ComponentDefinition;
import com.dream11.odin.dto.v1.ComponentProvisioningConfig;
import com.dream11.odin.dto.v1.ServiceDefinition;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith({VertxExtension.class})
class ComponentProvisioningMappingValidatorTest {
  private static ServiceDefinition serviceDefinition;

  @BeforeAll
  static void setUp() {
    ComponentDefinition component1 =
        ComponentDefinition.newBuilder()
            .setName("Component1")
            .setVersion("1.0.0")
            .setType("Type1")
            .build();

    ComponentDefinition component2 =
        ComponentDefinition.newBuilder()
            .setName("Component2")
            .setVersion("2.0.0")
            .setType("Type2")
            .addDependsOn("Component1")
            .build();

    serviceDefinition =
        ServiceDefinition.newBuilder()
            .setName("ServiceName")
            .setVersion("1.0.0")
            .setTeam("TeamName")
            .addComponents(component1)
            .addComponents(component2)
            .build();
  }

  @Test
  void shouldPassValidationWhenComponentNamesAreValid(
      Vertx vertx, VertxTestContext vertxTestContext) {
    vertx.runOnContext(
        __ -> {
          ComponentProvisioningMappingValidator validator =
              new ComponentProvisioningMappingValidator(
                  serviceDefinition,
                  List.of(
                      ComponentProvisioningConfig.newBuilder()
                          .setComponentName("Component1")
                          .build(),
                      ComponentProvisioningConfig.newBuilder()
                          .setComponentName("Component2")
                          .build()));

          validator.validate().subscribe(vertxTestContext::completeNow, vertxTestContext::failNow);
        });
  }

  @Test
  void shouldFailValidationWhenComponentNamesAreInvalid(
      Vertx vertx, VertxTestContext vertxTestContext) {
    vertx.runOnContext(
        __ -> {
          ComponentProvisioningMappingValidator validator =
              new ComponentProvisioningMappingValidator(
                  serviceDefinition,
                  List.of(
                      ComponentProvisioningConfig.newBuilder()
                          .setComponentName("Component3")
                          .build()));

          validator
              .validate()
              .subscribe(
                  () -> vertxTestContext.failNow("GRPCException should be thrown"),
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
