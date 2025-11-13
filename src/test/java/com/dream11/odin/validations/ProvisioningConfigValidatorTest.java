package com.dream11.odin.validations;

import com.dream11.odin.injector.GuiceInjector;
import com.dream11.odin.util.SharedDataUtil;
import com.google.inject.Guice;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith({VertxExtension.class})
class ProvisioningConfigValidatorTest {

  @Test
  void validate_WhenProvisioningConfigIsValid_ShouldReturnCompletedCompletable(
      Vertx vertx, VertxTestContext vertxTestContext) {

    vertx.runOnContext(
        __ -> {
          // Arrange
          ProvisioningConfigValidator validator =
              new ProvisioningConfigValidator(Collections.emptyList());

          GuiceInjector injector = new GuiceInjector(Guice.createInjector());
          SharedDataUtil.setInstance(vertx, injector);

          // Act and assert
          validator.validate().subscribe(vertxTestContext::completeNow, vertxTestContext::failNow);
        });
  }
}
