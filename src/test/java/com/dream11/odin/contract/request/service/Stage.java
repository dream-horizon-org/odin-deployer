package com.dream11.odin.contract.request.service;

import java.util.Map;
import lombok.Data;

@Data
public class Stage {
  Map<String, Object> config;
  String name;
}
