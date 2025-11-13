package com.dream11.odin.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith({VertxExtension.class})
class SharedDataUtilTest {

  @Test
  void testGetInstance(Vertx vertx) {
    // Arrange
    JsonObject jsonObject = new JsonObject().put("key", "value");
    SharedDataUtil.setInstance(vertx, jsonObject);

    // Act
    JsonObject getFromSharedData = SharedDataUtil.getInstance(vertx, JsonObject.class);

    // Assert
    assertThat(getFromSharedData).isEqualTo(jsonObject);
  }

  @Test
  void testGetInstanceFailIfNotFound(Vertx vertx) {
    // Act & Assert
    assertThatThrownBy(() -> SharedDataUtil.getInstance(vertx, JsonObject.class))
        .isInstanceOf(NoSuchElementException.class)
        .hasMessage("Cannot find default instance of io.vertx.core.json.JsonObject");
  }
}
