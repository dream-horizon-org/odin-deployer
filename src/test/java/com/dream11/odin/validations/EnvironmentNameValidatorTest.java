package com.dream11.odin.validations;

import static com.dream11.odin.constant.Constants.ORG_ID_PARAM;
import static com.dream11.odin.constant.Constants.USER_DETAILS;
import static com.dream11.odin.constant.Constants.USER_ID_PARAM;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dream11.grpc.error.GrpcException;
import com.dream11.odin.error.OdinError;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.ContextServerInterceptor;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class EnvironmentNameValidatorTest {

  @Test
  void succeedWhenNameMatchesRegex(Vertx vertx, VertxTestContext vertxTestContext) {
    vertx.runOnContext(
        __ -> {
          // Arrange
          String environmentName = "val-env1";
          Validator validator = new Validator();
          validator.add(new EnvironmentNameValidator(environmentName));
          JsonObject jsonObject = new JsonObject();
          jsonObject.put(ORG_ID_PARAM, 1);
          jsonObject.put(USER_ID_PARAM, 1);
          try (MockedStatic<ContextServerInterceptor> mockContext =
              Mockito.mockStatic(ContextServerInterceptor.class)) {
            mockContext.when(ContextServerInterceptor.get(USER_DETAILS)).thenReturn(jsonObject);
            assertEquals(ContextServerInterceptor.get(USER_DETAILS), jsonObject);

            // Act and Assert
            validator
                .validateAll()
                .subscribe(vertxTestContext::completeNow, vertxTestContext::failNow);
            vertxTestContext.completeNow();
          }
        });
  }

  @Test
  void failWhenNameDoesNotMatchRegex(Vertx vertx, VertxTestContext vertxTestContext) {
    vertx.runOnContext(
        __ -> {
          // Arrange
          String environmentName = "invalid_Env1";
          Validator validator = new Validator();
          validator.add(new EnvironmentNameValidator(environmentName));
          JsonObject jsonObject = new JsonObject();
          jsonObject.put(ORG_ID_PARAM, 1);
          jsonObject.put(USER_ID_PARAM, 1);

          try (MockedStatic<ContextServerInterceptor> mockContext =
              Mockito.mockStatic(ContextServerInterceptor.class)) {
            mockContext.when(ContextServerInterceptor.get(USER_DETAILS)).thenReturn(jsonObject);
            assertEquals(ContextServerInterceptor.get(USER_DETAILS), jsonObject);

            // Act and Assert
            validator
                .validateAll()
                .subscribe(
                    () -> vertxTestContext.failNow("GrpcException should be thrown"),
                    err -> {
                      try {
                        assertEquals(err.getClass(), GrpcException.class);
                        assertEquals(
                            err.getMessage(),
                            String.format(
                                OdinError.INVALID_ENV_NAME.getErrorMessage(), environmentName));
                        vertxTestContext.completeNow();
                      } catch (Exception e) {
                        vertxTestContext.failNow(e);
                      }
                    });
          }
        });
  }

  @Test
  void failWhenNameExceeds9Characters(Vertx vertx, VertxTestContext vertxTestContext) {
    vertx.runOnContext(
        __ -> {
          // Arrange
          String environmentName =
              "1234567890-1234567890-1234567890-1234567890-1234567890-1234567890";
          Validator validator = new Validator();
          validator.add(new EnvironmentNameValidator(environmentName));
          JsonObject jsonObject = new JsonObject();
          jsonObject.put(ORG_ID_PARAM, 1);
          jsonObject.put(USER_ID_PARAM, 1);

          try (MockedStatic<ContextServerInterceptor> mockContext =
              Mockito.mockStatic(ContextServerInterceptor.class)) {
            mockContext.when(ContextServerInterceptor.get(USER_DETAILS)).thenReturn(jsonObject);
            assertEquals(ContextServerInterceptor.get(USER_DETAILS), jsonObject);

            // Act and Assert
            validator
                .validateAll()
                .subscribe(
                    () -> vertxTestContext.failNow("GrpcException should be thrown"),
                    err -> {
                      try {
                        assertEquals(err.getClass(), GrpcException.class);
                        assertEquals(
                            err.getMessage(),
                            String.format(
                                OdinError.INVALID_ENV_NAME.getErrorMessage(), environmentName));
                        vertxTestContext.completeNow();
                      } catch (Exception e) {
                        vertxTestContext.failNow(e);
                      }
                    });
          }
        });
  }
}
