package com.dream11.odin.util;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.dto.v1.AccountInformation;
import com.dream11.odin.entity.EnvironmentAccount;
import com.dream11.odin.error.OdinError;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountResponse;
import io.vertx.core.json.JsonObject;
import java.util.List;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
public class AccountUtils {
  public AccountInformation getAccountInformation(JsonObject accounts) {
    GetProviderAccountResponse providerAccountResponse =
        JsonUtil.jsonToProtoBuilder(accounts, GetProviderAccountResponse.newBuilder()).build();
    return AccountInformation.newBuilder()
        .setServiceAccountsSnapshot(providerAccountResponse)
        .setProviderAccountName(providerAccountResponse.getAccount().getName())
        .build();
  }

  public AccountInformation getAccountInformation(EnvironmentAccount environmentAccount) {
    return AccountInformation.newBuilder()
        .setProviderAccountName(environmentAccount.accountName())
        .setStatus(
            EnvironmentUtil.getStatus(environmentAccount.action(), environmentAccount.status()))
        .setServiceAccountsSnapshot(
            JsonUtil.jsonToProtoBuilder(
                environmentAccount.accountData(), GetProviderAccountResponse.newBuilder()))
        .build();
  }

  @SneakyThrows
  public static AccountInformation filterAccount(
      List<AccountInformation> environmentProviderAccounts, String deploymentType) {

    String deploymentTypePrefix = deploymentType.split("_")[0];
    return environmentProviderAccounts.stream()
        .filter(
            accountInformation ->
                accountInformation
                    .getServiceAccountsSnapshot()
                    .getAccount()
                    .getProvider()
                    .equalsIgnoreCase(deploymentTypePrefix))
        .findFirst()
        .orElseThrow(
            () ->
                ExceptionUtil.getException(
                    OdinError.ACCOUNT_NOT_FOUND_FOR_COMPONENT_VALIDATION, deploymentType));
  }
}
