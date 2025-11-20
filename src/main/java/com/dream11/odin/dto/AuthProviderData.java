package com.dream11.odin.dto;

import com.dream11.odin.dto.auth.ProviderDetails;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthProviderData {
  private Long orgId;
  private String type;
  private ProviderDetails providerDetails;
}
