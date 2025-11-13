package com.dream11.odin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dream11.odin.MainModule;
import com.dream11.odin.config.AppConfig;
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
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

@ExtendWith(VertxExtension.class)
class JwtServiceTest {

  private JwtService jwtService;
  private String privateKey;
  private String publicKey;

  @BeforeEach
  void setUp(Vertx vertx) throws NoSuchAlgorithmException {
    GuiceInjector injector =
        new GuiceInjector(Guice.createInjector(List.of(new MainModule(vertx))));
    SharedDataUtil.setInstance(vertx, injector);

    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
    keyPairGenerator.initialize(256);
    KeyPair keyPair = keyPairGenerator.generateKeyPair();

    privateKey =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----";
    publicKey =
        "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
    jwtService =
        new JwtService(
            setupMockAppConfig(privateKey, publicKey), io.vertx.reactivex.core.Vertx.vertx());
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
  void testGenerateToken(Vertx vertx, VertxTestContext vertxTestContext) {
    vertx.runOnContext(
        __ -> {
          String subject = "testUser";
          Map<String, Object> claims = Collections.singletonMap("orgid", "123");

          jwtService
              .generateToken(subject, claims)
              .map(
                  token -> {
                    assertThat(token).isNotNull();

                    Claims parsedClaims =
                        Jwts.parser()
                            .verifyWith(jwtService.parsePublicKey(publicKey))
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();

                    assertThat(parsedClaims.getSubject()).isEqualTo(subject);
                    assertThat(parsedClaims.get("orgid")).isEqualTo("123");
                    return token;
                  })
              .subscribe(
                  ___ -> vertxTestContext.completeNow(), err -> vertxTestContext.failNow(err));
        });
  }

  @Test
  void testParseInvalidPrivateKey() {
    assertThatThrownBy(
            () ->
                new JwtService(
                    setupMockAppConfig("invalid", publicKey),
                    io.vertx.reactivex.core.Vertx.vertx()))
        .hasMessageContaining("Error parsing private key");
  }

  @Test
  void testParseInvalidPublicKey() {
    assertThatThrownBy(
            () ->
                new JwtService(
                    setupMockAppConfig(privateKey, "invalid"),
                    io.vertx.reactivex.core.Vertx.vertx()))
        .hasMessageContaining("Error parsing public key");
  }
}
