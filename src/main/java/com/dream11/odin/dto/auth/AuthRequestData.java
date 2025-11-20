package com.dream11.odin.dto.auth;

import static com.dream11.odin.constant.Constants.ANONYMOUS;
import static com.dream11.odin.constant.Constants.OIDC;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "providerType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = OIDCRequestData.class, name = OIDC),
  @JsonSubTypes.Type(value = AnonymousRequestData.class, name = ANONYMOUS)
})
public interface AuthRequestData {
  default void validate(Long orgId) {}
}
