package com.dream11.odin.auth;

import com.dream11.odin.dto.AuthProviderData;
import com.dream11.odin.dto.auth.AuthRequestData;
import io.reactivex.Single;

public interface AuthExecutor {
  Single<String> authorise(AuthProviderData authProviderData, AuthRequestData requestData);
}
