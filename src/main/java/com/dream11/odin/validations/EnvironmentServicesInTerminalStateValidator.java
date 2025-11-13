package com.dream11.odin.validations;

import static com.dream11.odin.constant.TaskStatus.FAILED;
import static com.dream11.odin.constant.TaskStatus.SUCCESSFUL;
import static com.dream11.odin.error.OdinError.ONE_OR_MORE_SERVICE_IN_NON_TERMINAL_STATE;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.ApplicationContext;
import com.dream11.odin.dao.EnvironmentDao;
import com.dream11.odin.dto.UserDetails;
import io.reactivex.Completable;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class EnvironmentServicesInTerminalStateValidator extends Validator {

  EnvironmentDao environmentDao;
  String environmentName;

  @Override
  public Completable validate() {
    final UserDetails userDetails = ApplicationContext.getUserDetails();
    return environmentDao
        .getEnvironmentWithServices(userDetails.getOrgId(), environmentName)
        .flatMapCompletable(
            environment -> {
              if (environment.getServicesList().stream()
                  .allMatch(
                      serviceTask ->
                          serviceTask.getStatus().endsWith(SUCCESSFUL.getValue())
                              || serviceTask.getStatus().endsWith(FAILED.getValue()))) {

                return Completable.complete();
              }

              return Completable.error(
                  ExceptionUtil.getException(ONE_OR_MORE_SERVICE_IN_NON_TERMINAL_STATE));
            });
  }
}
