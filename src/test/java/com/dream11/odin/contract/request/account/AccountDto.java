package com.dream11.odin.contract.request.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class AccountDto {
  Account account;

  @JsonProperty("linked_accounts")
  List<Account> linkedAccounts;
}
