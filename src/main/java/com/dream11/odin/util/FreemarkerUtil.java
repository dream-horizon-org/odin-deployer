package com.dream11.odin.util;

import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.val;

@UtilityClass
public class FreemarkerUtil {

  public String substituteValues(String name, String content, Map<String, Object> dataModel)
      throws IOException, TemplateException {
    val template = new Template(name, content, null);
    try (val out = new StringWriter()) {
      template.process(dataModel, out);
      return out.toString();
    }
  }
}
