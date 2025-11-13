package com.dream11.odin;

import com.dream11.odin.constant.Constants;
import com.dream11.odin.dto.UserDetails;
import io.vertx.grpc.ContextServerInterceptor;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@UtilityClass
public class ApplicationContext {

  public static void setUserDetails(UserDetails userDetails) {
    ContextServerInterceptor.put(Constants.USER_DETAILS, userDetails);
  }

  public static UserDetails getUserDetails() {
    return ContextServerInterceptor.get(Constants.USER_DETAILS);
  }

  public static String getTraceId() {
    return ContextServerInterceptor.getOrDefault(Constants.TRACE_ID, StringUtils.EMPTY);
  }

  public static void setTraceId(String traceId) {
    ContextServerInterceptor.put(Constants.TRACE_ID, traceId);
  }

  public static void setSearchAfterParam(List<Long> searchAfterParam) {
    ContextServerInterceptor.put(Constants.SEARCH_AFTER_PARAM, searchAfterParam);
  }

  public static List<Long> getSearchAfterParam() {
    return ContextServerInterceptor.getOrDefault(Constants.SEARCH_AFTER_PARAM, new ArrayList<>());
  }
}
