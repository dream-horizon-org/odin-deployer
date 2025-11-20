package com.dream11.odin.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.dream11.odin.MainModule;
import com.dream11.odin.auth.AuthExecutor;
import com.dream11.odin.auth.AuthExecutorFactory;
import com.dream11.odin.dao.AuthProviderDao;
import com.dream11.odin.dto.AuthProviderData;
import com.dream11.odin.dto.auth.AnonymousProviderDetails;
import com.dream11.odin.injector.GuiceInjector;
import com.dream11.odin.util.SharedDataUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.protobuf.Struct;
import io.reactivex.Single;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@ExtendWith(VertxExtension.class)
class AuthBusinessTest {

  @Mock private AuthProviderDao authProviderDao;

  @Mock private ObjectMapper objectMapper;

  @Mock private AuthExecutorFactory authExecutorFactory;

  @Mock private AuthExecutor authExecutor;

  private AuthBusiness authBusiness;

  @BeforeEach
  void setUp(Vertx vertx) {
    GuiceInjector injector =
        new GuiceInjector(Guice.createInjector(List.of(new MainModule(vertx))));
    SharedDataUtil.setInstance(vertx, injector);

    MockitoAnnotations.openMocks(this);

    authBusiness = new AuthBusiness(authProviderDao, authExecutorFactory, objectMapper);
  }

  @Test
  void testGetAuthProvider() {
    AuthProviderData authProviderData = new AuthProviderData();
    authProviderData.setType("anonymous");
    authProviderData.setProviderDetails(new AnonymousProviderDetails());
    when(authProviderDao.getAuthProviderData(anyLong())).thenReturn(Single.just(authProviderData));
    authBusiness
        .getAuthProvider(0L)
        .test()
        .assertNoErrors()
        .assertValue(response -> response.getType().equals("anonymous"));
  }

  @Test
  void testGetUserToken(Vertx vertx, VertxTestContext vertxTestContext) {
    vertx.runOnContext(
        __ -> {
          AuthProviderData authProviderData = new AuthProviderData();
          authProviderData.setType("anonymous");
          when(authProviderDao.getAuthProviderData(anyLong()))
              .thenReturn(Single.just(authProviderData));
          when(authExecutorFactory.getAuthExecutor(anyString())).thenReturn(authExecutor);
          when(authExecutor.authorise(any(), any())).thenReturn(Single.just("test-token"));

          authBusiness
              .getUserToken(1L, Struct.newBuilder().build())
              .subscribe(x -> vertxTestContext.completeNow(), t -> vertxTestContext.failNow(t));
        });
  }
}
