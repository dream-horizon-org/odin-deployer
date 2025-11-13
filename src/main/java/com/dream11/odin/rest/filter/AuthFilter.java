package com.dream11.odin.rest.filter;

import com.dream11.odin.ApplicationContext;
import com.dream11.odin.auth.JwtService;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.dto.UserDetails;
import com.dream11.odin.error.OdinRestError;
import com.dream11.rest.exception.RestException;
import com.google.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jboss.resteasy.core.interception.jaxrs.SuspendableContainerRequestContext;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AuthFilter implements ContainerRequestFilter {

  private final JwtService jwtService;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String authToken = requestContext.getHeaderString(Constants.AUTHORIZATION_KEY);

    if (requestContext.getUriInfo().getPath().equals("/healthcheck")) {
      return;
    }

    if (StringUtils.isEmpty(authToken)) {
      throw new RestException(OdinRestError.FORBIDDEN_EXCEPTION);
    }

    SuspendableContainerRequestContext suspendableRequestContext =
        (SuspendableContainerRequestContext) requestContext;
    suspendableRequestContext.suspend();

    jwtService
        .verifyToken(authToken)
        .map(
            claims ->
                UserDetails.builder()
                    .userId(claims.getSubject())
                    .orgId(claims.get("orgid", Integer.class).longValue())
                    .emailId(claims.getSubject())
                    .userToken(authToken)
                    .build())
        .subscribe(
            userDetails -> {
              ApplicationContext.setUserDetails(userDetails);
              suspendableRequestContext.resume();
            },
            err -> {
              log.error("Error while verifying user token {}", err.getMessage(), err);
              suspendableRequestContext.resume(err);
            });
  }
}
