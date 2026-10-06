package com.specificlanguages.mops.testing;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import jetbrains.mps.tool.common.Script;
import jetbrains.mps.tool.environment.Environment;
import jetbrains.mps.tool.run.ModuleClassCode;
import jetbrains.mps.lang.test.launcher.LaunchTestWorker;

public class AntTestWorker extends LaunchTestWorker {
  public AntTestWorker(Script script) { super(script); }
  public static void main(String[] args) {
    new AntTestWorker(Script.fromDumpInFile(new File(args[0]))).workFromMain();
  }
  @Override public void work() {
    System.out.println("MOPS_TEST_MODE=" + jetbrains.mps.RuntimeFlags.getTestMode());
    if (!jetbrains.mps.RuntimeFlags.isTestMode()) throw new AssertionError("Test mode required");
    try {
      ModuleClassCode code = new ModuleClassCode("c234a56a-502f-4751-aded-6f9846fff7ce(jetbrains.mps.lang.test.junit5)");
      code.load(myEnvironment.getPlatform(), "jetbrains.mps.lang.test.junit5.ScriptJUnit5Launcher");
      ClassLoader parent = code.instanceMethod("launchTests").orElseThrow().getDeclaringClass().getClassLoader();
      Class<?> launcher = parent.loadClass("jetbrains.mps.lang.test.junit5.ScriptJUnit5Launcher");
      String variant = LauncherApi.select(launcher);
      File jar = new File(System.getProperty("mops.test.adapters"), variant + ".jar");
      System.out.println("MOPS_TEST_ADAPTER=" + variant);
      try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toURI().toURL()}, parent)) {
        Class<?> adapter = loader.loadClass("com.specificlanguages.mops.testing.ModelLauncher");
        int failures = (Integer) adapter.getMethod("run", Script.class, Environment.class, Object.class).invoke(null, myWhatToDo, myEnvironment, this);
        System.out.println("MOPS_TEST_FAILURES=" + failures);
        failBuild("launchtests");
      }
    } catch (Exception e) { throw new RuntimeException(e); }
  }
}
