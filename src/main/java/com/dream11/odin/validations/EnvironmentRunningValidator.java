package com.dream11.odin.validations;

import static com.dream11.odin.error.OdinError.ENV_NOT_RUNNING;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.constant.EnvironmentStatus;
import com.dream11.odin.dao.EnvironmentDao;
import com.dream11.odin.dto.UserDetails;
import io.reactivex.Completable;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class EnvironmentRunningValidator extends Validator {
  EnvironmentDao environmentDao;
  String environmentName;
  UserDetails userDetails;

  @Override
  public Completable validate() {
    return this.environmentDao
        .getEnvironmentWithAccounts(userDetails.getOrgId(), this.environmentName)
        .map(
            envWithAccounts -> {
              if (!envWithAccounts
                  .getEnvironment()
                  .status()
                  .equals(EnvironmentStatus.RUNNING.name())) {
                throw ExceptionUtil.getException(
                    ENV_NOT_RUNNING,
                    envWithAccounts.getEnvironment().name(),
                    envWithAccounts.getEnvironment().status());
              }
              return Completable.complete();
            })
        .ignoreElement()
        .doOnError(err -> log.error("Error while validating env state {}", err.getMessage(), err));
  }
}
