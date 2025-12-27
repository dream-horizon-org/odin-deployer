package com.dream11.odin.dto.response;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NamespaceResponseData implements ResponseData {
  @NotBlank String accountName;
}
