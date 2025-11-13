package com.dream11.odin.validations;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.dto.v1.ComponentProvisioningConfig;
import com.dream11.odin.error.OdinError;
import com.dream11.odin.util.CompletableFutureUtil;
import com.dream11.odin.util.ComponentUtil;
import com.dream11.odin.util.ServiceUtil;
import io.reactivex.Completable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

@AllArgsConstructor
public class ProvisioningConfigValidator extends Validator {

  List<ComponentProvisioningConfig> componentProvisioningConfigs;

  @Override
  @SneakyThrows
  public Completable validate() {
    return CompletableFutureUtil.toCompletable(
            this.schemaParser
                .parse(ServiceUtil.readProvisioningConfigSchema())
                .validateAsync(
                    ComponentUtil.componentProvisioningConfigsToJsonArray(
                        componentProvisioningConfigs))
                .toCompletionStage()
                .toCompletableFuture())
        .onErrorResumeNext(
            err ->
                Completable.error(
                    ExceptionUtil.getException(
                        OdinError.INVALID_PROVISIONING_CONFIG_FILE, err.getMessage())));
  }
}
