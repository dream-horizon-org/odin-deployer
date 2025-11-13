package com.dream11.odin.service.responseprocessor;

import com.dream11.odin.dto.response.ResponseMessage;
import io.reactivex.Completable;

public interface ResponseProcessor {

  Completable process(ResponseMessage responseMessage);
}
