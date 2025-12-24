package com.dream11.odin.util;

import static com.dream11.odin.constant.Constants.KUBERNETES_PROVIDER_SERVICE_CATEGORY;

import com.dream11.odin.constant.Action;
import com.dream11.odin.constant.EnvironmentStatus;
import com.dream11.odin.constant.TaskStatus;
import com.dream11.odin.dto.v1.AccountInformation;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountResponse;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class EnvironmentUtil {
  private static final String STATUS_DELIMITER = "_";

  public static String getStatus(Action action, TaskStatus taskStatus) {
    if (Action.CREATE_ENVIRONMENT.equals(action) && TaskStatus.SUCCESSFUL.equals(taskStatus)) {
      return EnvironmentStatus.RUNNING.name();
    }
    if (Action.DELETE_ENVIRONMENT.equals(action) && TaskStatus.SUCCESSFUL.equals(taskStatus)) {
      return EnvironmentStatus.DELETED.name();
    }
    return String.join(STATUS_DELIMITER, action.name(), taskStatus.name());
  }

  public static List<String> getProviders(List<AccountInformation> accountInformationList) {

    return accountInformationList.stream()
        .map(AccountInformation::getServiceAccountsSnapshot)
        .map(r -> r.getAccount().getProvider())
        .toList();
  }

  public List<String> extractClusters(GetProviderAccountResponse providerAccountResponse) {
    return providerAccountResponse.getAccount().getServicesList().stream()
        .filter(
            psa ->
                psa.getCategory().equals(KUBERNETES_PROVIDER_SERVICE_CATEGORY)
                    && psa.getData().getFieldsMap().containsKey("clusters"))
        .flatMap(
            psa ->
                psa
                    .getData()
                    .getFieldsMap()
                    .get("clusters")
                    .getListValue()
                    .getValuesList()
                    .stream())
        .map(value -> value.getStructValue().getFieldsMap().get("name").getStringValue())
        .toList();
  }
}
