package com.dream11.odin.validations;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.error.OdinError;
import io.reactivex.Completable;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class EnvironmentNameValidator extends Validator {

  private static final String ENV_NAME_REGEX = "^(?=.{1,9}$)[a-z0-9][a-z0-9-]*[a-z0-9]$";
  private static final Pattern PATTERN = Pattern.compile(ENV_NAME_REGEX);

  String environmentName;

  @Override
  public Completable validate() {

    return Completable.fromAction(
        () -> {
          if (!PATTERN.matcher(environmentName).matches()) {
            throw ExceptionUtil.getException(OdinError.INVALID_ENV_NAME, environmentName);
          }
        });
  }
}
