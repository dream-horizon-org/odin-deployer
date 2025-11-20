package com.dream11.odin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.dream11.odin.MainModule;
import com.dream11.odin.config.AppConfig;
import com.dream11.odin.dto.AuthProviderData;
import com.dream11.odin.dto.auth.AnonymousProviderDetails;
import com.dream11.odin.dto.auth.AnonymousRequestData;
import com.dream11.odin.injector.GuiceInjector;
import com.dream11.odin.util.SharedDataUtil;
import com.google.inject.Guice;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

@ExtendWith(VertxExtension.class)
class AnonymousAuthExecutorTest {

  private JwtService jwtService;
  private AnonymousAuthExecutor anonymousAuthExecutor;
  private PublicKey publicKey;

  @BeforeEach
  void setUp(Vertx vertx) throws NoSuchAlgorithmException {
    GuiceInjector injector =
        new GuiceInjector(Guice.createInjector(List.of(new MainModule(vertx))));
    SharedDataUtil.setInstance(vertx, injector);

    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
    keyPairGenerator.initialize(256);
    KeyPair keyPair = keyPairGenerator.generateKeyPair();
    publicKey = keyPair.getPublic();

    String privateKeyString =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----";
    String publicKeyString =
        "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getEncoder().encodeToString(publicKey.getEncoded())
            + "\n-----END PUBLIC KEY-----";

    AppConfig appConfig = setupMockAppConfig(privateKeyString, publicKeyString);
    jwtService = new JwtService(appConfig, io.vertx.reactivex.core.Vertx.newInstance(vertx));
    anonymousAuthExecutor = new AnonymousAuthExecutor(jwtService);
  }

  private AppConfig setupMockAppConfig(String privateKeyString, String publicKeyString) {
    AppConfig appConfig = Mockito.mock(AppConfig.class);
    AuthConfig authConfig = Mockito.mock(AuthConfig.class);
    JwtConfig jwtConfig =
        JwtConfig.builder()
            .privateKey(privateKeyString)
            .publicKey(publicKeyString)
            .expirationMillis(3600000) // 1 hour
            .build();

    Mockito.when(appConfig.getAuthConfig()).thenReturn(authConfig);
    Mockito.when(authConfig.getJwtConfig()).thenReturn(jwtConfig);

    return appConfig;
  }

  @Test
  void testAuthoriseGeneratesValidToken(Vertx vertx, VertxTestContext vertxTestContext) {

    vertx.runOnContext(
        __ -> {
          // Act
          anonymousAuthExecutor
              .authorise(
                  new AuthProviderData(0L, "anonymous", new AnonymousProviderDetails()),
                  new AnonymousRequestData())
              .map(
                  token -> {
                    // Assert
                    assertThat(token).isNotNull();

                    Claims claims =
                        Jwts.parser()
                            .verifyWith(publicKey)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();

                    assertThat(claims.getSubject()).isEqualTo("anonymous");
                    assertThat(claims.get("orgid")).isEqualTo(0);
                    return token;
                  })
              .subscribe(
                  ___ -> vertxTestContext.completeNow(), err -> vertxTestContext.failNow(err));
        });
  }
}
