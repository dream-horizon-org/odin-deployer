package com.dream11.odin.contract.request.environment;

import com.dream11.odin.contract.request.RequestMessageBody;
import com.dream11.odin.contract.request.account.Account;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NamespaceRequestMessageBody implements RequestMessageBody {
  Account account;
  NamespaceAction action;
  String name;
  long orgId;
}
