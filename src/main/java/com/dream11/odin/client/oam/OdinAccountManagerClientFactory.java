package com.dream11.odin.client.oam;

import com.dream11.odin.grpc.provideraccount.v1.RxProviderAccountServiceGrpc;
import io.grpc.ManagedChannel;
import lombok.experimental.UtilityClass;

@UtilityClass
public class OdinAccountManagerClientFactory {

  public RxProviderAccountServiceGrpc.RxProviderAccountServiceStub getProviderAccountService(
      ManagedChannel channel) {
    return RxProviderAccountServiceGrpc.newRxStub(channel);
  }
}
