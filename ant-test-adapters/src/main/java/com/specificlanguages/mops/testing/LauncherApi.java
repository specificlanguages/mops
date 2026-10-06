package com.specificlanguages.mops.testing;

import java.lang.reflect.Constructor;
import java.util.Arrays;

/** Selects the separately compiled adapter before creating a project or executing tests. */
public final class LauncherApi {
  private LauncherApi() {}

  public static String select(Class<?> launcher) {
    boolean legacy = false;
    for (Constructor<?> constructor : launcher.getConstructors()) {
      String[] parameters = Arrays.stream(constructor.getParameterTypes()).map(Class::getName).toArray(String[]::new);
      if (Arrays.equals(parameters, new String[]{
          "jetbrains.mps.tool.environment.Environment", "jetbrains.mps.tool.common.TestData",
          "jetbrains.mps.tool.common.WorkerCallback", "java.io.File"})) return "current";
      if (Arrays.equals(parameters, new String[]{
          "jetbrains.mps.tool.common.Script", "jetbrains.mps.tool.environment.Environment",
          "jetbrains.mps.lang.test.launcher.WorkerCallback"})) legacy = true;
    }
    if (legacy) return "legacy";
    throw new IllegalStateException("Unsupported MPS test launcher API: " + launcher.getName());
  }
}
