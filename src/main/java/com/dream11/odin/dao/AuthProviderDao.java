package com.dream11.odin.dao;

import static com.dream11.odin.constant.Constants.ANONYMOUS;

import com.dream11.grpc.util.ExceptionUtil;
import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.dao.query.MysqlQuery;
import com.dream11.odin.dto.AuthProviderData;
import com.dream11.odin.dto.auth.ProviderDetails;
import com.dream11.odin.error.OdinError;
import com.dream11.odin.util.SingleUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.sqlclient.Row;
import io.vertx.reactivex.sqlclient.RowSet;
import io.vertx.reactivex.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AuthProviderDao {

  final MysqlClient mysqlClient;
  final ObjectMapper objectMapper;

  public Single<AuthProviderData> getAuthProviderData(Long orgId) {
    val readerMysqlClient = mysqlClient.getSlaveClient();
    return readerMysqlClient
        .preparedQuery(MysqlQuery.GET_AUTH_PROVIDER_FOR_ORG)
        .rxExecute(Tuple.of(orgId))
        .filter(rowset -> rowset.size() > 0)
        .switchIfEmpty(
            Single.error(ExceptionUtil.getException(OdinError.AUTH_PROVIDER_NOT_FOUND, orgId)))
        .map(rowSet -> buildAuthProviderData(rowSet, orgId))
        .compose(SingleUtil.applyDebugLogs(log));
  }

  private AuthProviderData buildAuthProviderData(RowSet<Row> rowSet, Long orgId) {
    Row row = rowSet.iterator().next();
    String type = row.getString("type");
    JsonObject providerDetailsJson = row.getJsonObject("provider_details");

    if (providerDetailsJson == null) {
      providerDetailsJson = new JsonObject();
    }

    JsonObject jsonWithType = providerDetailsJson.copy();
    jsonWithType.put("providerType", type != null ? type.toLowerCase() : ANONYMOUS);

    try {
      ProviderDetails providerDetails =
          objectMapper.readValue(jsonWithType.encode(), ProviderDetails.class);
      return new AuthProviderData(orgId, type, providerDetails);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to deserialize ProviderDetails for provider: " + type, e);
    }
  }
}
