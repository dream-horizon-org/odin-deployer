package com.dream11.odin.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;

/**
 * Custom Jackson serializer for protobuf Message objects. Converts protobuf messages to JSON using
 * protobuf's JsonFormat utility.
 *
 * <p>This is needed for integration tests that serialize InterceptorPayload containing protobuf
 * objects.
 */
public class ProtobufMessageSerializer extends JsonSerializer<Message> {

  private static final JsonFormat.Printer printer = JsonFormat.printer();

  @Override
  public void serialize(Message value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    if (value == null) {
      gen.writeNull();
      return;
    }
    // Convert protobuf message to JSON string
    String json = printer.print(value);
    // Write as raw JSON (not as a string)
    gen.writeRawValue(json);
  }
}
