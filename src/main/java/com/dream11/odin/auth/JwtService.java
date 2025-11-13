package com.dream11.odin.auth;

import com.dream11.odin.config.AppConfig;
import com.google.inject.Inject;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureAlgorithm;
import io.reactivex.Single;
import io.vertx.reactivex.core.Vertx;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JwtService {

  private final AppConfig appConfig;
  private final Vertx vertx;
  private final PrivateKey privateKey;
  private final PublicKey publicKey;
  private static final String ISSUER = "AUTH.ODIN";
  private final SignatureAlgorithm signatureAlgorithm;

  @Inject
  public JwtService(AppConfig appConfig, Vertx vertx) {
    this.vertx = vertx;
    this.signatureAlgorithm = Jwts.SIG.ES256;
    this.appConfig = appConfig;
    this.privateKey = parsePrivateKey(appConfig.getAuthConfig().getJwtConfig().getPrivateKey());
    this.publicKey = parsePublicKey(appConfig.getAuthConfig().getJwtConfig().getPublicKey());
  }

  public JwtService(AppConfig appConfig, Vertx vertx, SignatureAlgorithm signatureAlgorithm) {
    this.vertx = vertx;
    this.signatureAlgorithm = signatureAlgorithm;
    this.appConfig = appConfig;
    this.privateKey = parsePrivateKey(appConfig.getAuthConfig().getJwtConfig().getPrivateKey());
    this.publicKey = parsePublicKey(appConfig.getAuthConfig().getJwtConfig().getPublicKey());
  }

  public Single<String> generateToken(String subject, Map<String, Object> claims) {
    log.info("Generating JWT token for subject: {}", subject);

    return vertx
        .<String>rxExecuteBlocking(
            promise -> {
              Date now = new Date();
              Date expiryDate =
                  new Date(
                      now.getTime()
                          + appConfig.getAuthConfig().getJwtConfig().getExpirationMillis());

              String token =
                  Jwts.builder()
                      .subject(subject)
                      .issuer(ISSUER)
                      .claims(claims)
                      .issuedAt(now)
                      .expiration(expiryDate)
                      .signWith(privateKey, signatureAlgorithm)
                      .compact();

              log.info("Successfully generated JWT token for subject: {}", subject);
              promise.complete(token);
            })
        .toSingle();
  }

  public Single<Claims> verifyToken(String token) {

    return vertx
        .<Claims>rxExecuteBlocking(
            promise -> {
              try {
                Jws<Claims> jws =
                    Jwts.parser()
                        .verifyWith(publicKey) // verifies signature
                        .requireIssuer(ISSUER) // checks issuer
                        .build()
                        .parseSignedClaims(token);

                Claims claims = jws.getPayload();
                log.info(
                    "Valid token for subject: {}, expires at {}",
                    claims.getSubject(),
                    claims.getExpiration());

                promise.complete(claims);
              } catch (Exception e) {
                log.warn("Token verification failed", e);
                promise.fail(e);
              }
            })
        .toSingle();
  }

  PrivateKey parsePrivateKey(String privateKeyPem) {
    try {
      String privateKeyContent =
          privateKeyPem
              .replace("-----BEGIN PRIVATE KEY-----", "")
              .replace("-----END PRIVATE KEY-----", "")
              .replaceAll("\\s", "");

      byte[] decodedKey = Base64.getDecoder().decode(privateKeyContent);

      KeyFactory keyFactory = KeyFactory.getInstance("EC");
      PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decodedKey);
      return keyFactory.generatePrivate(keySpec);
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      log.error("Error parsing private key", e);
      throw new RuntimeException("Error parsing private key", e);
    }
  }

  PublicKey parsePublicKey(String publicKeyPem) {
    String pem =
        publicKeyPem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");

    byte[] der = Base64.getDecoder().decode(pem);
    X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
    try {
      return KeyFactory.getInstance("EC").generatePublic(spec);
    } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
      log.error("Error parsing public key", e);
      throw new RuntimeException("Error parsing public key", e);
    }
  }
}
