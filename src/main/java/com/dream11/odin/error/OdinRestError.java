package com.dream11.odin.error;

import com.dream11.rest.exception.RestError;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.apache.http.HttpStatus;

@Getter
@ToString
@RequiredArgsConstructor
public enum OdinRestError implements RestError {
  UNKNOWN_EXCEPTION(
      "UNKNOWN_EXCEPTION", "Something went wrong", HttpStatus.SC_INTERNAL_SERVER_ERROR),
  FORBIDDEN_EXCEPTION("FORBIDDEN_EXCEPTION", "Access denied!", HttpStatus.SC_FORBIDDEN),
  OPERATION_ID_INVALID("OPERATION_ID_INVALID", "OperationId is invalid", HttpStatus.SC_NOT_FOUND);

  final String errorCode;
  final String errorMessage;
  final int httpStatusCode;
}
