package com.dream11.odin.util;

import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.Vertx;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class ConfigUtil {
  public ConfigRetriever getConfigRetriever(Vertx vertx) {
    ConfigStoreOptions fileStore = new ConfigStoreOptions().setType("file").setFormat("hocon");
    ConfigStoreOptions defaultFileStore =
        new ConfigStoreOptions(fileStore)
            .setConfig(new JsonObject().put("path", "application-default.conf"));
    ConfigStoreOptions environmentFileStore =
        new ConfigStoreOptions(fileStore)
            .setConfig(new JsonObject().put("path", "application.conf"));
    ConfigStoreOptions sysPropsStore =
        new ConfigStoreOptions()
            .setType("sys")
            .setConfig(new JsonObject().put("hierarchical", true));

    return ConfigRetriever.create(
        vertx,
        new ConfigRetrieverOptions()
            .addStore(defaultFileStore)
            .addStore(environmentFileStore)
            .addStore(sysPropsStore));
  }
}
