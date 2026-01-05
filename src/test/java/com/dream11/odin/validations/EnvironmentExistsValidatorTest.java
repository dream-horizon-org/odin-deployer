package com.dream11.odin.validations;

import static com.dream11.odin.constant.Constants.ORG_ID_PARAM;
import static com.dream11.odin.constant.Constants.USER_DETAILS;
import static com.dream11.odin.constant.Constants.USER_ID_PARAM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.dream11.grpc.error.GrpcException;
import com.dream11.odin.ApplicationContext;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dao.EnvironmentDao;
import com.dream11.odin.dto.UserDetails;
import com.dream11.odin.dto.v1.Environment;
import com.dream11.odin.error.OdinError;
import com.dream11.odin.util.EnvironmentUtil;
import io.reactivex.Maybe;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.ContextServerInterceptor;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class EnvironmentExistsValidatorTest {

  @Mock EnvironmentDao environmentDao;

  @Test
  void succeedWhenEnvCreationFailed(Vertx vertx, VertxTestContext vertxTestContext) {

    vertx.runOnContext(
        __ -> {
          // Arrange
          Long orgId = 1L;
          String userId = "1";
          String environmentName = "test1";
          Validator validator = new Validator();
          validator.add(
              new EnvironmentExistsValidator(
                  environmentDao,
                  environmentName,
                  UserDetails.builder().orgId(orgId).userId(userId).build()));
          JsonObject jsonObject = new JsonObject();
          jsonObject.put(ORG_ID_PARAM, 1);
          jsonObject.put(USER_ID_PARAM, 1);
          when(environmentDao.getEnvironmentWithAccountsIfExists(orgId, environmentName))
              .thenReturn(
                  Maybe.just(
                      Environment.newBuilder()
                          .setStatus(
                              EnvironmentUtil.getStatus(
                                  Action.CREATE_ENVIRONMENT, TaskStatus.FAILED))
                          .build()));
          try (MockedStatic<ContextServerInterceptor> mockContext =
              Mockito.mockStatic(ContextServerInterceptor.class)) {
            mockContext.when(ContextServerInterceptor.get(USER_DETAILS)).thenReturn(jsonObject);

            // Set the authentication context
            ApplicationContext.setUserDetails(
                UserDetails.builder().userId(userId).orgId(orgId).build());

            assertEquals(ContextServerInterceptor.get(USER_DETAILS), jsonObject);

            // Act and Assert
            validator
                .validateAll()
                .subscribe(vertxTestContext::completeNow, vertxTestContext::failNow);
          }
        });
  }

  @Test
  void failWhenEnvExistsInCreation(Vertx vertx, VertxTestContext vertxTestContext) {
    vertx.runOnContext(
        __ -> {
          // Arrange
          Long orgId = 1L;
          String userId = "1";
          String environmentName = "test1";
          Validator validator = new Validator();
          validator.add(
              new EnvironmentExistsValidator(
                  environmentDao,
                  environmentName,
                  UserDetails.builder().orgId(orgId).userId(userId).build()));
          JsonObject jsonObject = new JsonObject();
          jsonObject.put(ORG_ID_PARAM, orgId);
          jsonObject.put(USER_ID_PARAM, userId);
          when(environmentDao.getEnvironmentWithAccountsIfExists(orgId, environmentName))
              .thenReturn(
                  Maybe.just(
                      Environment.newBuilder()
                          .setStatus(
                              EnvironmentUtil.getStatus(
                                  Action.CREATE_ENVIRONMENT, TaskStatus.IN_PROGRESS))
                          .build()));
          try (MockedStatic<ContextServerInterceptor> mockContext =
              Mockito.mockStatic(ContextServerInterceptor.class)) {
            mockContext.when(ContextServerInterceptor.get(USER_DETAILS)).thenReturn(jsonObject);

            // Set the authentication context
            ApplicationContext.setUserDetails(
                UserDetails.builder().userId(userId).orgId(orgId).build());

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
                                OdinError.ENV_ALREADY_EXISTS.getErrorMessage(),
                                EnvironmentUtil.getStatus(
                                    Action.CREATE_ENVIRONMENT, TaskStatus.IN_PROGRESS)));
                        vertxTestContext.completeNow();
                      } catch (Throwable e) {
                        vertxTestContext.failNow(e);
                      }
                    });
          }
        });
  }
}
