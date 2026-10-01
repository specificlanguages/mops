package com.specificlanguages.mops.testing;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import jetbrains.mps.tool.environment.Environment;
import jetbrains.mps.baseLanguage.unitTest.platform.*;
import jetbrains.mps.smodel.MPSModuleRepository;
import jetbrains.mps.classloading.ClassLoaderManager;
import org.jetbrains.mps.openapi.model.*;
import org.jetbrains.mps.openapi.module.SModule;
import org.junit.platform.launcher.*;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;

/** Filters stock discovery and forwards every event to the stock failure listener. */
public final class SelectionSupport {
  private final Map<String, TestDescriptor> sources = new HashMap<>();
  private final Set<Class<?>> selected = new HashSet<>();
  private final boolean filter = System.getProperty("mops.test.model") != null;
  private static int sequence;

  public SelectionSupport(Environment environment) {
    MPSModuleRepository repository = environment.getPlatform().findComponent(MPSModuleRepository.class);
    TestPlatform platform;
    try {
      platform = (TestPlatform) TestPlatform.class.getMethod("getInstance").invoke(null);
    } catch (NoSuchMethodException e) {
      try {
        platform = (TestPlatform) jetbrains.mps.components.ComponentHost.class.getMethod("findComponent", Class.class).invoke(environment.getPlatform(), TestPlatform.class);
      } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    final TestPlatform tests = platform;
    ClassLoaderManager loaders = environment.getPlatform().findComponent(ClassLoaderManager.class);
    repository.getModelAccess().runReadAction(() -> {
      Set<String> modules = new HashSet<>(Arrays.asList(System.getProperty("mops.test.modules").split(",")));
      for (SModule module : repository.getModules()) {
        if (!modules.contains(module.getModuleId().toString())) continue;
        for (SModel model : module.getModels()) {
          if (jetbrains.mps.smodel.SModelStereotype.isStubModel(model)) continue;
          boolean modelSelected = model.getReference().toString().equals(System.getProperty("mops.test.model"));
          for (SNode root : model.getRootNodes()) {
            tests.getAggregateDiscoveryParticipant().discover(root, new TestDiscoveryRequest(new TestDescriptor())).ifPresent(d -> {
              sources.put(d.getFullName(), d);
              if (modelSelected && (System.getProperty("mops.test.node") == null || root.getReference().toString().equals(System.getProperty("mops.test.node"))))
                try { selected.add(loaders.getClassLoader(module).loadClass(d.getFullName())); }
                catch (ClassNotFoundException failure) { throw new IllegalStateException("Cannot load " + d.getFullName() + " (" + root.getReference() + ")", failure); }
            });
          }
        }
      }
    });
  }

  public boolean accept(Class<?> type) { return !filter || selected.contains(type); }

  private String source(String klass, String method) {
    TestDescriptor descriptor = sources.get(klass);
    if (descriptor == null) return null;
    if (method != null) {
      for (TestDescriptor child : descriptor.getTests())
        if (method.equals(child.getShortName())) { descriptor = child; break; }
    }
    return descriptor.getSource() instanceof SNodeTestSource ? ((SNodeTestSource) descriptor.getSource()).getNodeReference().toString() : null;
  }

  private void event(TestIdentifier id, String status, String detail) {
    Properties event = new Properties();
    event.setProperty("id", id.getUniqueId());
    event.setProperty("name", id.getDisplayName());
    event.setProperty("kind", id.isTest() ? "TEST" : "CONTAINER");
    event.setProperty("status", status);
    Object src = id.getSource().orElse(null);
    String klass = src instanceof MethodSource ? ((MethodSource) src).getClassName() : src instanceof ClassSource ? ((ClassSource) src).getClassName() : null;
    String method = src instanceof MethodSource ? ((MethodSource) src).getMethodName() : null;
    if (klass != null) event.setProperty("className", klass);
    if (method != null) event.setProperty("methodName", method);
    String reference = source(klass, method);
    if (reference != null) event.setProperty("source", reference);
    if (detail != null) event.setProperty("detail", detail);
    publish(event);
  }

  private static synchronized void publish(Properties event) {
    try {
      Path directory = Paths.get(System.getProperty("mops.test.events"));
      Path pending = directory.resolve("pending");
      try (OutputStream out = Files.newOutputStream(pending)) { event.store(out, null); }
      Files.move(pending, directory.resolve(String.format("%08d.properties", sequence++)), StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) { throw new UncheckedIOException(e); }
  }

  public TestExecutionListener listener(TestExecutionListener stock) {
    return new TestExecutionListener() {
      @Override public void testPlanExecutionStarted(TestPlan plan) {
        long count = plan.countTestIdentifiers(id -> id.isTest() || id.getSource().orElse(null) instanceof MethodSource);
        Properties event = new Properties();
        event.setProperty("discovered", Long.toString(count));
        publish(event);
        stock.testPlanExecutionStarted(plan);
      }
      @Override public void testPlanExecutionFinished(TestPlan plan) {
        stock.testPlanExecutionFinished(plan);
        Properties event = new Properties(); event.setProperty("finished", "true"); publish(event);
      }
      @Override public void dynamicTestRegistered(TestIdentifier id) { stock.dynamicTestRegistered(id); }
      @Override public void executionStarted(TestIdentifier id) { event(id, "RUNNING", null); stock.executionStarted(id); }
      @Override public void executionSkipped(TestIdentifier id, String reason) { event(id, "SKIPPED", reason); stock.executionSkipped(id, reason); }
      @Override public void executionFinished(TestIdentifier id, TestExecutionResult result) {
        String detail = null;
        if (result.getThrowable().isPresent()) {
          StringWriter buffer = new StringWriter(); result.getThrowable().get().printStackTrace(new PrintWriter(buffer)); detail = buffer.toString();
        }
        event(id, result.getStatus() == TestExecutionResult.Status.SUCCESSFUL ? "PASSED" : result.getStatus().toString(), detail);
        stock.executionFinished(id, result);
      }
      @Override public void reportingEntryPublished(TestIdentifier id, org.junit.platform.engine.reporting.ReportEntry entry) { stock.reportingEntryPublished(id, entry); }
    };
  }
}
