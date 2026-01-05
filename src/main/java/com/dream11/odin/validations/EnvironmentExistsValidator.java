package com.dream11.odin.validations;

import static com.dream11.odin.error.OdinError.ENV_ALREADY_EXISTS;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dao.EnvironmentDao;
import com.dream11.odin.dto.UserDetails;
import com.dream11.odin.util.EnvironmentUtil;
import io.reactivex.Completable;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class EnvironmentExistsValidator extends Validator {

  private static final List<String> VALID_EXISTING_STATUS =
      Collections.singletonList(
          EnvironmentUtil.getStatus(Action.DELETE_ENVIRONMENT, TaskStatus.SUCCESSFUL));

  final EnvironmentDao environmentDao;

  String environmentName;

  UserDetails userDetails;

  @Override
  public Completable validate() {
    return this.environmentDao
        .getEnvironmentWithAccountsIfExists(userDetails.getOrgId(), this.environmentName)
        .doOnSuccess(
            environment -> {
              if (!VALID_EXISTING_STATUS.contains(environment.getEnvironment().status())) {
                throw ExceptionUtil.getException(
                    ENV_ALREADY_EXISTS, environment.getEnvironment().status());
              }
            })
        .ignoreElement();
  }
}
