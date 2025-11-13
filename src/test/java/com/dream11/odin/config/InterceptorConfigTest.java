package com.dream11.odin.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterceptorConfigTest {

  private Validator validator;

  @BeforeEach
  void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @Test
  void testSetComponentUrls() {
    // Arrange
    InterceptorConfig config = new InterceptorConfig();
    List<String> urls = List.of("http://interceptor1.local", "http://interceptor2.local");

    // Act
    config.setComponent(urls);

    // Assert
    assertThat(config.getComponent()).hasSize(2);
    assertThat(config.getComponent())
        .containsExactly("http://interceptor1.local", "http://interceptor2.local");
  }

  @Test
  void testSetHttpConfig() {
    // Arrange
    InterceptorConfig config = new InterceptorConfig();
    InterceptorConfig.InterceptorHttpConfig httpConfig =
        new InterceptorConfig.InterceptorHttpConfig();
    httpConfig.setTimeout(60);
    httpConfig.setRetryCount(5);

    // Act
    config.setConfig(httpConfig);

    // Assert
    assertThat(config.getConfig().getTimeout()).isEqualTo(60);
    assertThat(config.getConfig().getRetryCount()).isEqualTo(5);
  }

  @Test
  void shouldPassValidationWhenConfigIsValid() {
    // Arrange
    InterceptorConfig config = new InterceptorConfig();
    config.setComponent(List.of("http://interceptor.local"));
    InterceptorConfig.InterceptorHttpConfig httpConfig =
        new InterceptorConfig.InterceptorHttpConfig();
    httpConfig.setTimeout(3);
    httpConfig.setRetryCount(2);
    config.setConfig(httpConfig);

    // Act
    Set<ConstraintViolation<InterceptorConfig>> violations = validator.validate(config);

    // Assert
    assertThat(violations).isEmpty();
  }

  @Test
  void shouldFailValidationWhenComponentIsNull() {
    // Arrange
    InterceptorConfig config = new InterceptorConfig();
    config.setComponent(null);

    // Act
    Set<ConstraintViolation<InterceptorConfig>> violations = validator.validate(config);

    // Assert
    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("component"));
  }

  @Test
  void shouldFailValidationWhenConfigIsNull() {
    // Arrange
    InterceptorConfig config = new InterceptorConfig();
    config.setConfig(null);

    // Act
    Set<ConstraintViolation<InterceptorConfig>> violations = validator.validate(config);

    // Assert
    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("config"));
  }

  @Test
  void shouldFailHttpConfigValidationWhenTimeoutIsNull() {
    // Arrange
    InterceptorConfig.InterceptorHttpConfig httpConfig =
        new InterceptorConfig.InterceptorHttpConfig();
    httpConfig.setTimeout(null);

    // Act
    Set<ConstraintViolation<InterceptorConfig.InterceptorHttpConfig>> violations =
        validator.validate(httpConfig);

    // Assert
    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("timeout"));
  }

  @Test
  void shouldFailHttpConfigValidationWhenRetryCountIsNull() {
    // Arrange
    InterceptorConfig.InterceptorHttpConfig httpConfig =
        new InterceptorConfig.InterceptorHttpConfig();
    httpConfig.setRetryCount(null);

    // Act
    Set<ConstraintViolation<InterceptorConfig.InterceptorHttpConfig>> violations =
        validator.validate(httpConfig);

    // Assert
    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("retryCount"));
  }

  @Test
  void shouldPassHttpConfigValidationWhenCustomValuesAreValid() {
    // Arrange
    InterceptorConfig.InterceptorHttpConfig httpConfig =
        new InterceptorConfig.InterceptorHttpConfig();
    httpConfig.setTimeout(120);
    httpConfig.setRetryCount(10);

    // Act
    Set<ConstraintViolation<InterceptorConfig.InterceptorHttpConfig>> violations =
        validator.validate(httpConfig);

    // Assert
    assertThat(violations).isEmpty();
    assertThat(httpConfig.getTimeout()).isEqualTo(120);
    assertThat(httpConfig.getRetryCount()).isEqualTo(10);
  }

  @Test
  void shouldBeValidWhenComponentListIsEmpty() {
    // Arrange
    InterceptorConfig config = new InterceptorConfig();
    config.setComponent(new ArrayList<>()); // Empty but not null
    InterceptorConfig.InterceptorHttpConfig httpConfig =
        new InterceptorConfig.InterceptorHttpConfig();
    httpConfig.setTimeout(3);
    httpConfig.setRetryCount(2);
    config.setConfig(httpConfig);

    // Act
    Set<ConstraintViolation<InterceptorConfig>> violations = validator.validate(config);

    // Assert
    assertThat(violations).isEmpty();
  }
}
