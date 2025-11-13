package com.dream11.odin.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

public class FilesUtil {
  public static void deleteDirectory(File dir) throws IOException {
    try (var dirStream = Files.walk(Paths.get(dir.getPath()))) {
      dirStream.map(Path::toFile).sorted(Comparator.reverseOrder()).forEach(File::delete);
    }
  }
}
