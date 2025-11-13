package com.dream11.odin.validations;

import static com.dream11.odin.constant.Constants.USER_DETAILS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.dream11.grpc.error.GrpcException;
import com.dream11.odin.ApplicationContext;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dao.EnvironmentDao;
import com.dream11.odin.dto.UserDetails;
import com.dream11.odin.dto.v1.Environment;
import com.dream11.odin.dto.v1.ServiceTask;
import com.dream11.odin.error.OdinError;
import io.reactivex.Single;
import io.vertx.core.Vertx;
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
public class EnvironmentServicesInTerminalStateValidatorTest {

  @Mock EnvironmentDao environmentDao;

  @Test
  void failValidationWhenServiceDeploymentInProgress(
      Vertx vertx, VertxTestContext vertxTestContext) {

    vertx.runOnContext(
        __ -> {
          // Arrange
          Long orgId = 1L;
          String userId = "1";
          String environmentName = "test1";
          Validator validator = new Validator();
          validator.add(
              new EnvironmentServicesInTerminalStateValidator(environmentDao, environmentName));
          UserDetails userDetails = UserDetails.builder().orgId(1L).userId("1").build();

          when(environmentDao.getEnvironmentWithServices(orgId, environmentName))
              .thenReturn(
                  Single.just(
                      Environment.newBuilder()
                          .addServices(
                              ServiceTask.newBuilder()
                                  .setStatus(TaskStatus.IN_PROGRESS.getValue())
                                  .build())
                          .build()));
          try (MockedStatic<ContextServerInterceptor> mockContext =
              Mockito.mockStatic(ContextServerInterceptor.class)) {
            mockContext.when(ContextServerInterceptor.get(USER_DETAILS)).thenReturn(userDetails);

            // Set the authentication context
            ApplicationContext.setUserDetails(
                UserDetails.builder().userId(userId).orgId(orgId).build());

            assertEquals(ContextServerInterceptor.get(USER_DETAILS), userDetails);

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
                                OdinError.ONE_OR_MORE_SERVICE_IN_NON_TERMINAL_STATE
                                    .getErrorMessage()));
                        vertxTestContext.completeNow();
                      } catch (Throwable e) {
                        vertxTestContext.failNow(e);
                      }
                    });
          }
        });
  }

  @Test
  void succeedValidationWhenServiceDeploymentNotInProgress(
      Vertx vertx, VertxTestContext vertxTestContext) {

    vertx.runOnContext(
        __ -> {
          // Arrange
          Long orgId = 1L;
          String userId = "1";
          String environmentName = "test1";
          Validator validator = new Validator();
          validator.add(
              new EnvironmentServicesInTerminalStateValidator(environmentDao, environmentName));
          UserDetails userDetails = UserDetails.builder().orgId(1L).userId("1").build();

          when(environmentDao.getEnvironmentWithServices(orgId, environmentName))
              .thenReturn(
                  Single.just(
                      Environment.newBuilder()
                          .addServices(
                              ServiceTask.newBuilder()
                                  .setStatus(TaskStatus.SUCCESSFUL.getValue())
                                  .build())
                          .build()));
          try (MockedStatic<ContextServerInterceptor> mockContext =
              Mockito.mockStatic(ContextServerInterceptor.class)) {
            mockContext.when(ContextServerInterceptor.get(USER_DETAILS)).thenReturn(userDetails);

            // Set the authentication context
            ApplicationContext.setUserDetails(
                UserDetails.builder().userId(userId).orgId(orgId).build());

            assertEquals(ContextServerInterceptor.get(USER_DETAILS), userDetails);

            // Act and Assert
            validator
                .validateAll()
                .subscribe(vertxTestContext::completeNow, vertxTestContext::failNow);
            vertxTestContext.completeNow();
          }
        });
  }
}
