package com.specificlanguages.mops.testing;
import java.util.*;
import jetbrains.mps.tool.common.Script;
import jetbrains.mps.tool.environment.Environment;
import jetbrains.mps.tool.common.WorkerCallback;
import jetbrains.mps.tool.common.TestData;
import java.io.File;
import jetbrains.mps.library.*;
import jetbrains.mps.library.contributor.*;
import jetbrains.mps.core.tool.environment.util.SetLibraryContributor;
import jetbrains.mps.vfs.VFSManager;
import jetbrains.mps.vfs.openapi.FileSystem;
import jetbrains.mps.persistence.PersistenceRegistry;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.ClassSelector;
import jetbrains.mps.lang.test.junit5.ScriptJUnit5Launcher;
import org.junit.platform.launcher.TestExecutionListener;

public class ModelLauncher extends ScriptJUnit5Launcher {
  public ModelLauncher(Environment env, TestData tests, WorkerCallback callback, File project) { super(env, tests, callback, project); }
  public static int run(Script script, Environment env, Object callback) {
    FileSystem fs = env.getPlatform().findComponent(VFSManager.class).getUmbrellaFileSystemJavaIO();
    ModulesMiner miner = new ModulesMiner(env.getPlatform());
    Set<LibDescriptor> paths = new LinkedHashSet<>();
    for (File module : script.getModules()) {
      paths.add(new LibDescriptor(fs.getFile(module)));
      miner.collectModules(fs.getFile(module));
    }
    env.getPlatform().findComponent(LibraryInitializer.class).load(Collections.singletonList(SetLibraryContributor.fromSet("TestModules", paths)));
    TestData plan = new TestData();
    PersistenceRegistry persistence = env.getPlatform().findComponent(PersistenceRegistry.class);
    for (ModulesMiner.ModuleHandle handle : miner.getCollectedModules())
      plan.testModules.add(new TestData.ModuleRecord(persistence.asString(handle.getDescriptor().getModuleReference()), true));
    File project = script.getProjectDirectories().isEmpty() ? null : script.getProjectDirectories().get(0);
    ModelLauncher launcher = new ModelLauncher(env, plan, (WorkerCallback) callback, project);
    String reports = script.getProperty("launchtests.testReportsDir");
    if (reports != null) {
      new File(reports).mkdirs();
      if (Boolean.parseBoolean(script.getProperty("launchtests.testReportsOpenTest"))) launcher.opentestReport(new File(reports));
      else launcher.legacyXmlReport(new File(reports));
    }
    if (script.getProperty("teamcity.version") != null) launcher.teamcityReport();
    return launcher.launchTests();
  }
  @Override protected void launchTestsWithSession(List<DiscoverySelector> classes, TestExecutionListener listener) {
    SelectionSupport support = new SelectionSupport(myEnvironment);
    List<DiscoverySelector> filtered = new ArrayList<>(classes);
    filtered.removeIf(s -> !(s instanceof ClassSelector) || !support.accept(((ClassSelector) s).getJavaClass()));
    if (filtered.isEmpty()) throw new IllegalArgumentException("No tests discovered for the selection");
    super.launchTestsWithSession(filtered, support.listener(listener));
  }
}
