package com.dream11.odin.contract.request.service;

import com.dream11.odin.contract.request.account.AccountDto;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class ComponentAction {

  Map<String, Object> baseConfig;
  Map<String, Object> flavourConfig;
  Map<String, Object> operationConfig;
  String name;
  String type;
  String version;
  List<Integer> dependsOn;
  String deploymentType;
  Integer id;
  Stage stage;
  String provider;
  AccountDto accounts;
}
