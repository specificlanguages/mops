package com.specificlanguages.mops.testing;
import java.util.*;
import jetbrains.mps.tool.common.Script;
import jetbrains.mps.tool.environment.Environment;
import jetbrains.mps.lang.test.launcher.WorkerCallback;
import jetbrains.mps.lang.test.junit5.ScriptJUnit5Launcher;
import org.junit.platform.launcher.TestExecutionListener;

public class ModelLauncher extends ScriptJUnit5Launcher {
  public ModelLauncher(Script script, Environment env, WorkerCallback callback) { super(script, env, callback); }
  public static int run(Script script, Environment env, Object callback) {
    return new ModelLauncher(script, env, (WorkerCallback) callback).launchTests();
  }
  @Override public void launchTestsWithSession(Collection<Class<?>> classes, TestExecutionListener listener) {
    SelectionSupport support = new SelectionSupport(myEnvironment);
    List<Class<?>> filtered = new ArrayList<>(classes);
    filtered.removeIf(c -> !support.accept(c));
    if (filtered.isEmpty()) throw new IllegalArgumentException("No tests discovered for the selection");
    super.launchTestsWithSession(filtered, support.listener(listener));
  }
}
