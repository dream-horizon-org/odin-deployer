package com.dream11.odin.util;

import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import io.vertx.core.json.JsonObject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class ApplicationUtil {

  static List<Integer> nonReadableColourCodes = List.of(0, 16, 17, 18, 52, 53);

  public int generateIntegerUUID() {
    SecureRandom random = new SecureRandom(); // Compliant for security-sensitive use cases
    return random.nextInt(1000, 100000);
  }

  @SneakyThrows
  public Struct.Builder toGrpcStruct(JsonObject jsonObject) {
    Struct.Builder structBuilder = Struct.newBuilder();
    JsonFormat.parser().merge(jsonObject.encode(), structBuilder);
    return structBuilder;
  }

  public String compressAndEncode(String data) {
    try {
      byte[] inputBytes = data.getBytes(StandardCharsets.UTF_8);

      Deflater deflater = new Deflater();
      deflater.setInput(inputBytes);
      deflater.finish();

      byte[] buffer = new byte[1024];
      int compressedDataLength;

      try (java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream()) {
        while (!deflater.finished()) {
          compressedDataLength = deflater.deflate(buffer);
          outputStream.write(buffer, 0, compressedDataLength);
        }
        // Encode the compressed data since it may contain non-ascii characters
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
      } finally {
        deflater.end();
      }
    } catch (Exception e) {
      log.error("Error while compressing data {}", e.getMessage(), e);
      return data;
    }
  }

  public static String getANSIColorForComponent(String componentName) {
    CRC32 crc = new CRC32();
    crc.update(componentName.getBytes(StandardCharsets.UTF_8));
    int code = (int) (crc.getValue() % 230);
    // increment till non readable colour code contains code
    while (nonReadableColourCodes.contains(code)) {
      code = (code + 1) % 230;
    }
    return "\u001B[38;5;%dm".formatted(code);
  }

  public <T> void validate(T object) {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();
      Set<ConstraintViolation<T>> constraintViolations = validator.validate(object);
      if (!constraintViolations.isEmpty()) {
        throw new ConstraintViolationException(constraintViolations);
      }
    }
  }
}
