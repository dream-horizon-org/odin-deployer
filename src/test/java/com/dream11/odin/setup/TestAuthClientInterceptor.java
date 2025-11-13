package com.dream11.odin.setup;

import com.dream11.odin.auth.JwtConfig;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import lombok.SneakyThrows;

public class TestAuthClientInterceptor implements ClientInterceptor {
  private final JwtConfig jwtConfig;
  private final PrivateKey privateKey;

  @SneakyThrows
  public TestAuthClientInterceptor() {
    Config config = ConfigFactory.load("application-default.conf");
    Config jwtConfigObject = config.getConfig("authConfig.jwtConfig");

    // Directly construct JwtConfig from the typesafe config object
    this.jwtConfig =
        new JwtConfig(
            jwtConfigObject.getString("privateKey"),
            jwtConfigObject.getString("publicKey"),
            jwtConfigObject.getLong("expirationMillis"));

    this.privateKey = parsePrivateKey(this.jwtConfig.getPrivateKey());
  }

  private String generateToken(String subject) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + jwtConfig.getExpirationMillis());
    Map<String, Object> claims = new HashMap<>();
    claims.put("orgid", 1L);

    return Jwts.builder()
        .subject(subject)
        .issuer("AUTH.ODIN")
        .claims(claims)
        .issuedAt(now)
        .expiration(expiryDate)
        .signWith(privateKey, Jwts.SIG.ES256)
        .compact();
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
    return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
        next.newCall(method, callOptions)) {
      @Override
      public void start(Listener<RespT> responseListener, Metadata headers) {
        String token = generateToken("anonymous");
        headers.put(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), token);
        super.start(responseListener, headers);
      }
    };
  }

  private PrivateKey parsePrivateKey(String privateKeyPem) {
    try {
      String privateKeyContent =
          privateKeyPem
              .replaceAll("-----BEGIN PRIVATE KEY-----", "")
              .replaceAll("-----END PRIVATE KEY-----", "")
              .replaceAll("\\s", "");

      byte[] decodedKey = Base64.getDecoder().decode(privateKeyContent);

      KeyFactory keyFactory = KeyFactory.getInstance("EC");
      PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decodedKey);
      return keyFactory.generatePrivate(keySpec);
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new RuntimeException("Error parsing private key", e);
    }
  }
}
