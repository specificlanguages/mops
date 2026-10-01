package com.specificlanguages.mops.testing;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;
import jetbrains.mps.build.ant.junit.LaunchTestTask;

/** Adds the adapter boot classes after Ant's filtering of the calling JVM's classpath. */
public final class MopsLaunchTestTask extends LaunchTestTask {
  @Override protected Set<File> calculateClassPath(boolean fork) {
    Set<File> paths = new LinkedHashSet<>(super.calculateClassPath(fork));
    paths.add(new File(getProject().getProperty("mops.test.boot")));
    return paths;
  }
}
