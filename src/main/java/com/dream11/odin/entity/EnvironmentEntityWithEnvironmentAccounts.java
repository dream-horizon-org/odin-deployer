package com.dream11.odin.entity;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class EnvironmentEntityWithEnvironmentAccounts {
  EnvironmentEntity environment;
  List<EnvironmentAccount> environmentAccounts;
}
