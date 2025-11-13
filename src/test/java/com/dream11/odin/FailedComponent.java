package com.dream11.odin;

import com.dream11.odin.constant.Action;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;

@Data
public class FailedComponent {
  private static Map<Pair<String, Action>, List<Pair<String, Action>>> failedServiceComponents =
      new ConcurrentHashMap<>();
  private static Map<String, Action> failedNamespaces = new ConcurrentHashMap<>();

  public static void addFailedServiceComponent(
      Pair<String, Action> failedService, List<Pair<String, Action>> failedComponents) {
    failedServiceComponents.put(failedService, failedComponents);
  }

  public static void addFailedNamespace(String namespace, Action action) {
    failedNamespaces.put(namespace, action);
  }

  public static List<Pair<String, Action>> getFailedComponents(Pair<String, Action> failedService) {
    return failedServiceComponents.get(failedService);
  }

  public static Action getFailedNamespace(String namespace) {
    return failedNamespaces.get(namespace);
  }

  public static boolean containsFailedServiceComponent(Pair<String, Action> failedService) {
    return failedServiceComponents.containsKey(failedService);
  }

  public static boolean containsFailedNamespace(String namespace) {
    return failedNamespaces.containsKey(namespace);
  }

  public static boolean containsFailedNamespace(String namespace, Action action) {
    return failedNamespaces.containsKey(namespace)
        && failedNamespaces.get(namespace).equals(action);
  }

  public static void reset() {
    failedServiceComponents.clear();
    failedNamespaces.clear();
  }
}
