package com.dream11.odin.auth;

import static com.dream11.odin.constant.Constants.ORGID;

import com.dream11.odin.dto.AuthProviderData;
import com.dream11.odin.dto.auth.AuthRequestData;
import com.google.inject.Inject;
import io.reactivex.Single;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AnonymousAuthExecutor implements AuthExecutor {

  private final JwtService jwtService;

  @Override
  public Single<String> authorise(AuthProviderData authProviderData, AuthRequestData requestData) {
    log.info("Issuing anonymous JWT token");
    String userId = "anonymous";
    return jwtService.generateToken(userId, Map.of(ORGID, authProviderData.getOrgId()));
  }
}
