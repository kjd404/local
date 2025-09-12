package org.artificers;

import java.util.Arrays;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

public class RunAllTests {
  public static void main(String[] args) {
    SummaryGeneratingListener listener = new SummaryGeneratingListener();
    DiscoverySelector[] selectors =
        new DiscoverySelector[] {
          DiscoverySelectors.selectPackage("org.artificers.ingest.app"),
          DiscoverySelectors.selectPackage("org.artificers.ingest.cli"),
          DiscoverySelectors.selectPackage("org.artificers.ingest.csv"),
          DiscoverySelectors.selectPackage("org.artificers.ingest.di"),
          DiscoverySelectors.selectPackage("org.artificers.ingest.model"),
          DiscoverySelectors.selectPackage("org.artificers.ingest.service"),
          DiscoverySelectors.selectPackage("org.artificers.jooq")
        };
    LauncherDiscoveryRequest request =
        LauncherDiscoveryRequestBuilder.request().selectors(Arrays.asList(selectors)).build();
    Launcher launcher = LauncherFactory.create();
    launcher.registerTestExecutionListeners(listener);
    launcher.execute(request);
    TestExecutionSummary summary = listener.getSummary();
    summary.printTo(new java.io.PrintWriter(System.out));
    if (summary.getTotalFailureCount() > 0) {
      System.err.println("Tests failed: " + summary.getFailures());
      System.exit(1);
    }
  }
}
