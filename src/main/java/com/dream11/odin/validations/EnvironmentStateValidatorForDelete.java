package com.dream11.odin.validations;

import static com.dream11.odin.error.OdinError.ENV_CANNOT_BE_DELETED;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.ApplicationContext;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dao.EnvironmentDao;
import com.dream11.odin.dto.UserDetails;
import com.dream11.odin.util.EnvironmentUtil;
import io.reactivex.Completable;
import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class EnvironmentStateValidatorForDelete extends Validator {

  private static final List<String> VALID_STATUS =
      Arrays.asList(
          EnvironmentUtil.getStatus(Action.CREATE_ENVIRONMENT, TaskStatus.SUCCESSFUL),
          EnvironmentUtil.getStatus(Action.CREATE_ENVIRONMENT, TaskStatus.FAILED),
          EnvironmentUtil.getStatus(Action.DELETE_ENVIRONMENT, TaskStatus.FAILED));

  EnvironmentDao environmentDao;
  String environmentName;

  @Override
  public Completable validate() {
    final UserDetails userDetails = ApplicationContext.getUserDetails();
    return this.environmentDao
        .getEnvironmentByNameAndIsActiveIfExists(userDetails.getOrgId(), environmentName)
        .flatMapCompletable(
            environment -> {
              if (!VALID_STATUS.contains(environment.getStatus())) {
                return Completable.error(
                    ExceptionUtil.getException(ENV_CANNOT_BE_DELETED, environment.getStatus()));
              }
              return Completable.complete();
            });
  }
}
