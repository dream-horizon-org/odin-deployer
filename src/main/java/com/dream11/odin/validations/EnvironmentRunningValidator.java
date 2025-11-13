package com.dream11.odin.validations;

import static com.dream11.odin.error.OdinError.ENV_DOES_NOT_EXIST;
import static com.dream11.odin.error.OdinError.ENV_NOT_RUNNING;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.constant.EnvironmentStatus;
import com.dream11.odin.dao.EnvironmentDao;
import com.dream11.odin.dto.UserDetails;
import io.reactivex.Completable;
import io.reactivex.Single;
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
        .getEnvironmentByNameAndIsActiveIfExists(userDetails.getOrgId(), this.environmentName)
        .switchIfEmpty(
            Single.error(ExceptionUtil.getException(ENV_DOES_NOT_EXIST, this.environmentName)))
        .map(
            environment -> {
              if (!environment.getStatus().equals(EnvironmentStatus.RUNNING.name())) {
                throw ExceptionUtil.getException(
                    ENV_NOT_RUNNING, environment.getName(), environment.getStatus());
              }
              return Completable.complete();
            })
        .ignoreElement()
        .doOnError(err -> log.error("Error while validating env state {}", err.getMessage(), err));
  }
}
