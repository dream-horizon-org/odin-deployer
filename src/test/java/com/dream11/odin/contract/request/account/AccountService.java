package com.dream11.odin.contract.request.account;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountService {
  Integer id;

  String name;

  String category;

  Map<String, Object> data;
}
