package com.dream11.odin.validations;

import static com.dream11.odin.error.OdinError.ENV_ALREADY_EXISTS;

import com.dream11.grpc.util.ExceptionUtil;
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
public class EnvironmentExistsValidator extends Validator {

  private static final List<String> VALID_STATUS =
      Arrays.asList(
          EnvironmentUtil.getStatus(Action.CREATE_ENVIRONMENT, TaskStatus.IN_PROGRESS),
          EnvironmentUtil.getStatus(Action.CREATE_ENVIRONMENT, TaskStatus.SUCCESSFUL),
          EnvironmentUtil.getStatus(Action.DELETE_ENVIRONMENT, TaskStatus.IN_PROGRESS),
          EnvironmentUtil.getStatus(Action.DELETE_ENVIRONMENT, TaskStatus.FAILED));

  final EnvironmentDao environmentDao;

  String environmentName;

  UserDetails userDetails;

  @Override
  public Completable validate() {
    return this.environmentDao
        .getEnvironmentByNameAndIsActiveIfExists(userDetails.getOrgId(), this.environmentName)
        .doOnSuccess(
            environment -> {
              if (VALID_STATUS.contains(environment.getStatus())) {
                throw ExceptionUtil.getException(ENV_ALREADY_EXISTS, environment.getStatus());
              }
            })
        .doOnError(err -> log.error("Error {}", err.getMessage(), err))
        .ignoreElement();
  }
}
