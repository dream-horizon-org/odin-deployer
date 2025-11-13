package com.dream11.odin.validations;

import static org.assertj.core.api.Assertions.assertThat;

import com.dream11.grpc.error.GrpcException;
import com.dream11.odin.dto.v1.ComponentDefinition;
import com.dream11.odin.dto.v1.ServiceDefinition;
import com.dream11.odin.injector.GuiceInjector;
import com.dream11.odin.util.SharedDataUtil;
import com.google.inject.Guice;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith({VertxExtension.class})
class ServiceDefinitionValidatorTest {

  @Test
  void validate_WhenServiceDefinitionIsValidAndNoCyclicDependency_ShouldReturnCompletedCompletable(
      Vertx vertx, VertxTestContext vertxTestContext) {
    vertx.runOnContext(
        __ -> {
          // Arrange
          ServiceDefinition serviceDefinition = createServiceDefinition();
          ServiceDefinitionValidator validator = new ServiceDefinitionValidator(serviceDefinition);

          GuiceInjector injector = new GuiceInjector(Guice.createInjector());
          SharedDataUtil.setInstance(vertx, injector);

          // Act and assert
          validator.validate().subscribe(vertxTestContext::completeNow, vertxTestContext::failNow);
        });
  }

  @Test
  void validate_WhenCyclicDependencyExists_ShouldThrowIllegalArgumentException(
      Vertx vertx, VertxTestContext vertxTestContext) {
    vertx.runOnContext(
        __ -> {
          // Arrange
          ServiceDefinition serviceDefinition = createServiceDefinitionWithCyclicDependency();
          ServiceDefinitionValidator validator = new ServiceDefinitionValidator(serviceDefinition);

          GuiceInjector injector = new GuiceInjector(Guice.createInjector());
          SharedDataUtil.setInstance(vertx, injector);

          // Act + Assert
          validator
              .validate()
              .subscribe(
                  () -> vertxTestContext.failNow("GRPCException should be thrown"),
                  err -> {
                    try {
                      assertThat(err).isInstanceOf(GrpcException.class);
                      assertThat(err.getMessage())
                          .isEqualTo("Cyclic dependency present in service definition");
                      vertxTestContext.completeNow();
                    } catch (Throwable t) {
                      vertxTestContext.failNow(t);
                    }
                  });
        });
  }

  private ServiceDefinition createServiceDefinitionWithCyclicDependency() {
    ComponentDefinition component1 =
        ComponentDefinition.newBuilder()
            .setName("component1")
            .setVersion("1.0")
            .setType("mysql")
            .addAllDependsOn(Collections.singletonList("component2"))
            .build();
    ComponentDefinition component2 =
        ComponentDefinition.newBuilder()
            .setName("component2")
            .setVersion("1.0")
            .setType("mysql")
            .addAllDependsOn(Collections.singletonList("component3"))
            .build();
    ComponentDefinition component3 =
        ComponentDefinition.newBuilder()
            .setName("component3")
            .setVersion("1.0")
            .setType("mysql")
            .addAllDependsOn(
                Collections.singletonList("component1")) // Introducing cyclic dependency
            .build();

    return ServiceDefinition.newBuilder()
        .setName("service1")
        .setVersion("1.0")
        .setTeam("team1")
        .addAllComponents(Arrays.asList(component1, component2, component3))
        .build();
  }

  private ServiceDefinition createServiceDefinition() {
    ComponentDefinition component1 =
        ComponentDefinition.newBuilder()
            .setName("component1")
            .setVersion("1.0")
            .setType("mysql")
            .addAllDependsOn(Collections.singletonList("component2"))
            .build();
    ComponentDefinition component2 =
        ComponentDefinition.newBuilder()
            .setName("component2")
            .setVersion("1.0")
            .setType("mysql")
            .addAllDependsOn(Collections.singletonList("component3"))
            .build();
    ComponentDefinition component3 =
        ComponentDefinition.newBuilder()
            .setName("component3")
            .setVersion("1.0")
            .setType("mysql")
            .build();

    return ServiceDefinition.newBuilder()
        .setName("service1")
        .setVersion("1.0")
        .setTeam("team1")
        .addAllComponents(Arrays.asList(component1, component2, component3))
        .build();
  }
}
