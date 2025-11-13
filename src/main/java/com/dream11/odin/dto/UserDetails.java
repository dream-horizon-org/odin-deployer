package com.dream11.odin.dto;

import static com.dream11.odin.constant.Constants.ORG_ID_PARAM;
import static com.dream11.odin.constant.Constants.USER_EMAIL_PARAM;
import static com.dream11.odin.constant.Constants.USER_ID_PARAM;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserDetails {
  @JsonProperty(USER_ID_PARAM)
  String userId;

  @JsonProperty(ORG_ID_PARAM)
  Long orgId;

  @JsonProperty(USER_EMAIL_PARAM)
  String emailId;

  String userToken;
}
