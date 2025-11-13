package com.dream11.odin.oam;

import static com.dream11.odin.constant.Constants.EKS_PROVIDER_SERVICE_CATEGORY;

import com.dream11.odin.dto.v1.ProviderAccount;
import com.dream11.odin.dto.v1.ProviderServiceAccount;
import com.dream11.odin.grpc.provideraccount.v1.GetAllProviderAccountsRequest;
import com.dream11.odin.grpc.provideraccount.v1.GetAllProviderAccountsResponse;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountRequest;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountResponse;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountsRequest;
import com.dream11.odin.grpc.provideraccount.v1.GetProviderAccountsResponse;
import com.dream11.odin.grpc.provideraccount.v1.RxProviderAccountServiceGrpc;
import com.dream11.odin.util.JsonUtil;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Objects;
import org.apache.commons.io.FileUtils;

public class MockOAMProviderAccountService
    extends RxProviderAccountServiceGrpc.ProviderAccountServiceImplBase {

  public static final String accountNameWoCluster = "accWoCluster";
  public static final String accountNameWCluster = "accWCluster";
  public static final GetProviderAccountResponse accountWithoutClusterResponse;

  public static final GetProviderAccountResponse accountWithClusterResponse;

  static {
    String oamAccountResponseFile = "oam/accountWithCluster.json";
    try {
      ClassLoader classLoader = MockOAMProviderAccountService.class.getClassLoader();
      File file =
          new File(
              Objects.requireNonNull(classLoader.getResource(oamAccountResponseFile)).getFile());
      String content =
          FileUtils.readFileToString(new File(file.getAbsolutePath()), Charset.defaultCharset());

      accountWithClusterResponse =
          JsonUtil.jsonToProtoBuilder(
                  new JsonObject(content), GetProviderAccountResponse.newBuilder())
              .build();

      accountWithoutClusterResponse =
          GetProviderAccountResponse.newBuilder()
              .setAccount(
                  ProviderAccount.newBuilder()
                      .setName(accountNameWoCluster)
                      .setProvider("gcp")
                      .addServices(
                          ProviderServiceAccount.newBuilder()
                              .setName("EKS")
                              .setCategory(EKS_PROVIDER_SERVICE_CATEGORY)))
              .build();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Single<GetProviderAccountResponse> getProviderAccount(
      Single<GetProviderAccountRequest> request) {
    return request.map(
        req ->
            accountNameWoCluster.equals(req.getName())
                ? accountWithoutClusterResponse
                : accountWithClusterResponse);
  }

  @Override
  public Single<GetAllProviderAccountsResponse> getAllProviderAccounts(
      Single<GetAllProviderAccountsRequest> request) {
    return request.map(
        req ->
            GetAllProviderAccountsResponse.newBuilder()
                .addAccounts(accountWithClusterResponse)
                .addAccounts(accountWithoutClusterResponse)
                .build());
  }

  @Override
  public Single<GetProviderAccountsResponse> getProviderAccounts(
      Single<GetProviderAccountsRequest> request) {
    return request.map(
        req ->
            GetProviderAccountsResponse.newBuilder()
                .addAccounts(accountWithClusterResponse)
                .addAccounts(accountWithoutClusterResponse)
                .build());
  }
}
