package com.dream11.odin.client.oam;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OdinAccountManagerConfig {

  @NotBlank String host;

  @NotNull Integer port;

  @NotNull Channel channel;
}
