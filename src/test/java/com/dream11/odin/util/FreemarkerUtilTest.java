package com.dream11.odin.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import freemarker.template.TemplateException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FreemarkerUtilTest {

  @Test
  void testGoodCase() throws TemplateException, IOException {
    String content = "Hello ${USER}!";
    Map<String, Object> dataModel = new HashMap<>();
    dataModel.put("USER", "testUser");
    String result = FreemarkerUtil.substituteValues("", content, dataModel);
    assertEquals("Hello testUser!", result);
  }
}
