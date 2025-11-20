package com.dream11.odin.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AnonymousRequestData implements AuthRequestData {
  @JsonProperty("providerType")
  private String providerType;

  @Override
  public void validate(Long orgId) {
    // Anonymous auth requires no validation
  }
}
