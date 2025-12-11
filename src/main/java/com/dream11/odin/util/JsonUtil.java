package com.dream11.odin.util;

import com.dream11.odin.injector.GuiceInjector;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.*;
import com.google.protobuf.util.JsonFormat;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.sqlclient.Row;
import io.vertx.reactivex.sqlclient.RowSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.commons.text.StringSubstitutor;

@UtilityClass
public class JsonUtil {

  public JsonObject getJsonObjectFromNestedJson(JsonObject json, String flattenedKey) {
    JsonObject cur = json;
    String[] keys = flattenedKey.split("\\.");
    for (String key : keys) {
      if (!cur.containsKey(key)) {
        return new JsonObject();
      }
      cur = cur.getJsonObject(key);
    }
    return cur;
  }

  @SneakyThrows
  public <T extends Message.Builder> T jsonToProtoBuilder(JsonObject json, T builder) {
    return jsonStringToProtoBuilder(json.encode(), builder);
  }

  @SneakyThrows
  public <T extends Message.Builder> T jsonStringToProtoBuilder(String jsonString, T builder) {
    JsonFormat.parser().ignoringUnknownFields().merge(jsonString, builder);
    return builder;
  }

  @SneakyThrows
  public JsonObject getJsonFromProto(Message message, String... keys) {
    List<String> keyList = Arrays.asList(keys);
    JsonObject result = new JsonObject(JsonFormat.printer().print(message));
    if (keyList.isEmpty()) {
      return result;
    }
    return JsonObject.mapFrom(
        result.stream()
            .filter(entry -> keyList.contains(entry.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
  }

  @SneakyThrows
  public Map<String, Object> getMapFromProto(Message message) {
    return SharedDataUtil.getInstance(GuiceInjector.class)
        .getInstance(ObjectMapper.class)
        .readValue(JsonFormat.printer().preservingProtoFieldNames().print(message), HashMap.class);
  }

  @SneakyThrows
  public Map<String, Object> getMapFromJsonObjectString(String jsonString) {
    return SharedDataUtil.getInstance(GuiceInjector.class)
        .getInstance(ObjectMapper.class)
        .readValue(jsonString, HashMap.class);
  }

  public <T> List<T> rowSetToList(RowSet<Row> rows, Class<T> clazz) throws JsonProcessingException {
    ObjectMapper objectMapper = getObjectMapper();
    List<T> list = new ArrayList<>();

    for (Row row : rows) {
      list.add(objectMapper.readValue(row.toJson().toString(), clazz));
    }
    return list;
  }

  public Struct replaceStructValues(Struct originalStruct, Map<String, String> replacements) {
    JsonObject json = JsonUtil.getJsonFromProto(originalStruct);
    json = customReplace(json, replacements);
    String updatedStruct = StringSubstitutor.replace(json, replacements);
    return JsonUtil.jsonToProtoBuilder(new JsonObject(updatedStruct), Struct.newBuilder()).build();
  }

  public String replaceValues(String jsonString, Map<String, String> replacements) {
    JsonObject json = new JsonObject(jsonString);
    json = customReplace(json, replacements);
    return StringSubstitutor.replace(json, replacements);
  }

  private static JsonObject customReplace(JsonObject json, Map<String, String> replacements) {
    String jsonStr = json.encode();
    for (Map.Entry<String, String> entry : replacements.entrySet()) {
      jsonStr = jsonStr.replaceAll("\\$" + entry.getKey(), entry.getValue());
    }
    return new JsonObject(jsonStr);
  }

  public JsonObject mergeJsonObjects(JsonObject json1, JsonObject json2) {
    if (json1 == null) {
      return json2;
    } else if (json2 == null) {
      return json1;
    }
    JsonObject mergedJson = json1.copy();
    for (Map.Entry<String, Object> entry : json2) {
      String key = entry.getKey();
      Object value = entry.getValue();
      if (value instanceof JsonObject commonValue
          && mergedJson.containsKey(key)
          && mergedJson.getValue(key) instanceof JsonObject) {
        mergedJson.put(key, mergeJsonObjects(mergedJson.getJsonObject(key), commonValue));
      } else {
        mergedJson.put(key, value);
      }
    }
    return mergedJson;
  }

  public JsonObject sortJsonObject(JsonObject jsonObject) {
    JsonNode node;
    try {
      node = getObjectMapper().readTree(jsonObject.toString());
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Error while converting jsonObject to jsonNode", e);
    }
    return JsonObject.mapFrom(sortJsonNode(node));
  }

  public static JsonNode sortJsonNode(JsonNode jsonNode) {
    ObjectMapper objectMapper = getObjectMapper();
    if (jsonNode.isObject()) {
      ObjectNode sortedNode = objectMapper.createObjectNode();
      TreeMap<String, JsonNode> sortedMap = new TreeMap<>();
      jsonNode
          .fields()
          .forEachRemaining(entry -> sortedMap.put(entry.getKey(), sortJsonNode(entry.getValue())));
      sortedMap.forEach(sortedNode::set);
      return sortedNode;
    } else if (jsonNode.isArray()) {
      ArrayNode sortedArray = objectMapper.createArrayNode();
      ArrayNode finalSortedArray = sortedArray;
      jsonNode.forEach(element -> finalSortedArray.add(sortJsonNode(element)));
      sortedArray = sortArrayNode(sortedArray);
      return sortedArray;
    } else {
      return jsonNode;
    }
  }

  private static ArrayNode sortArrayNode(ArrayNode arrayNode) {
    List<JsonNode> nodeList = new ArrayList<>();
    arrayNode.forEach(nodeList::add);
    nodeList.sort(Comparator.comparing(JsonNode::toString));
    ArrayNode sortedArray = getObjectMapper().createArrayNode();
    nodeList.forEach(sortedArray::add);
    return sortedArray;
  }

  @SneakyThrows
  public JsonNode convertProtoToJsonNode(Struct config) {
    String jsonString = JsonFormat.printer().print(config);
    return getObjectMapper().readTree(jsonString);
  }

  @SneakyThrows
  public JsonNode convertToJsonNode(String jsonString) {
    return getObjectMapper().readTree(jsonString);
  }

  @SneakyThrows
  public JsonObject convertToJsonSorted(String jsonString) {
    return JsonObject.mapFrom(sortJsonNode(getObjectMapper().readTree(jsonString)));
  }

  public JsonObject convertProtoToJsonSorted(Struct config) {
    return JsonObject.mapFrom(sortJsonNode(convertProtoToJsonNode(config)));
  }

  public JsonObject convertProtoToJson(Struct config) {
    return JsonObject.mapFrom(convertProtoToJsonNode(config));
  }

  private ObjectMapper getObjectMapper() {
    return SharedDataUtil.getInstance(GuiceInjector.class).getInstance(ObjectMapper.class);
  }
}
