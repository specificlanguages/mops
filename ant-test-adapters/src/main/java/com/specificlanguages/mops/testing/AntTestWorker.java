package com.specificlanguages.mops.testing;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import jetbrains.mps.ide.platform.watching.ReloadManager;
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
    refreshFromDisk(System.getProperty("mops.test.sources").split(File.pathSeparator));
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

  /**
   * Brings the VFS up to date with the given folders on disk and lets MPS reload what changed. A reused IntelliJ system
   * directory keeps VFS records from earlier runs, and the test-mode platform skips its initial VFS refresh. The
   * files the VFS has stored under the folders are loaded into memory before the refresh; docs/test-running-design.md
   * explains why. ExternalProjectRefresh in the daemon loads stored files the same way: keep the two in step.
   */
  private static void refreshFromDisk(String[] folders) {
    List<VirtualFile> files = new ArrayList<>();
    for (String folder : folders) {
      VirtualFile file = LocalFileSystem.getInstance().findFileByNioFile(Path.of(folder));
      if (file != null) files.add(file);
    }
    ApplicationManager.getApplication().invokeAndWait(() -> {
      for (VirtualFile file : files) loadStoredFiles(file);
      VfsUtil.markDirtyAndRefresh(false, true, true, files.toArray(VirtualFile.EMPTY_ARRAY));
      ReloadManager.getInstance().flush();
    });
  }

  /** Loads into memory the files the VFS has stored under the folder, without listing folders on disk. */
  private static void loadStoredFiles(VirtualFile folder) {
    VfsUtilCore.visitChildrenRecursively(folder, new VirtualFileVisitor<Void>() {
      @Override public Iterable<VirtualFile> getChildrenIterable(VirtualFile file) {
        return ((NewVirtualFile) file).iterInDbChildren();
      }
    });
  }
}
