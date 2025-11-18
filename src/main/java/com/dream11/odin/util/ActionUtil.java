package com.dream11.odin.util;

import com.dream11.odin.constant.Action;
import com.dream11.odin.dto.ComponentData;
import com.dream11.odin.dto.ComponentIdentifier;
import com.dream11.odin.dto.requestqueue.ComponentAction;
import com.dream11.odin.dto.requestqueue.Stage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ActionUtil {

  /**
   * Create list of similar component actions for each component in component data
   *
   * @param componentDataMap Map of ComponentIdentifier, ComponentData
   * @param stageName Name of stage (action)
   * @param stageConfig Config of stage (action)
   * @return List of ComponentAction
   */
  public List<ComponentAction> buildComponentActions(
      Map<ComponentIdentifier, ComponentData> componentDataMap,
      Action stageName,
      Map<String, Object> stageConfig) {
    Map<String, Stage> componentsStageMap =
        ComponentUtil.generateAllComponentStages(
            componentDataMap.keySet().stream().map(ComponentIdentifier::getComponentName).toList(),
            stageName,
            stageConfig);

    return ComponentUtil.getComponentActions(componentDataMap, componentsStageMap);
  }

  /**
   * Removes component dependency from dependsOn where a dependent component is not present in
   * definition This method is used when any component is skipped for deploy and as a result
   *
   * @param componentActions List of ComponentAction
   * @return Filtered List of ComponentAction
   */
  public List<ComponentAction> filterSkippedDependencies(List<ComponentAction> componentActions) {
    List<Integer> validDependencies =
        componentActions.stream().map(ComponentAction::getId).toList();

    return componentActions.stream()
        .map(
            componentAction -> {
              List<Integer> updatedDependsOn =
                  componentAction.getDependsOn().stream()
                      .filter(validDependencies::contains)
                      .collect(Collectors.toSet())
                      .stream()
                      .toList();
              // remove previous dependencies because withDependsOn appends dependsOn list
              componentAction.setDependsOn(new ArrayList<>());
              return componentAction.addDependsOn(updatedDependsOn);
            })
        .toList();
  }
}
