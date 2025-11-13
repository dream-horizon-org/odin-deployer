package com.dream11.odin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.dream11.odin.client.WebClient;
import com.dream11.odin.client.WebClientFactory;
import com.dream11.odin.config.AppConfig;
import io.vertx.reactivex.core.Vertx;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AuthExecutorFactoryTest {

  @Mock private AppConfig appConfig;

  @Mock private AuthConfig authConfig;

  @Mock private JwtConfig jwtConfig;

  private AuthExecutorFactory authExecutorFactory;

  @BeforeEach
  void setUp() throws NoSuchAlgorithmException {
    MockitoAnnotations.openMocks(this);
    when(appConfig.getAuthConfig()).thenReturn(authConfig);
    when(authConfig.getJwtConfig()).thenReturn(jwtConfig);

    // Generate a dummy key pair
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
    keyPairGenerator.initialize(256);
    KeyPair keyPair = keyPairGenerator.generateKeyPair();
    String privateKey =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----";
    String publicKey =
        "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";

    when(jwtConfig.getPrivateKey()).thenReturn(privateKey);
    when(jwtConfig.getPublicKey()).thenReturn(publicKey);

    Vertx vertx = Vertx.vertx();
    WebClient webClient = WebClientFactory.getDefaultInstance(vertx);
    authExecutorFactory = new AuthExecutorFactory(new JwtService(appConfig, vertx), webClient);
  }

  @Test
  void testGetAuthExecutor() {
    AuthExecutor authExecutor = authExecutorFactory.getAuthExecutor("anonymous");
    assertThat(authExecutor).isInstanceOf(AnonymousAuthExecutor.class);
  }
}
