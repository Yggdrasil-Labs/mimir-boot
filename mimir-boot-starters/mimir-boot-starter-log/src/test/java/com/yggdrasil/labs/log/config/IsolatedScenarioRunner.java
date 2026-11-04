package com.yggdrasil.labs.log.config;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.io.PrintWriter;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/** 子 JVM 入口：以 JUnit Platform 运行指定场景类，按失败数决定退出码 */
public final class IsolatedScenarioRunner {

    private IsolatedScenarioRunner() {}

    public static void main(String[] args) throws Exception {
        LauncherDiscoveryRequest request =
                LauncherDiscoveryRequestBuilder.request()
                        .selectors(selectClass(Class.forName(args[0])))
                        .build();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        Launcher launcher = LauncherFactory.create();
        launcher.execute(request, listener);
        TestExecutionSummary summary = listener.getSummary();
        PrintWriter out = new PrintWriter(System.out, true);
        summary.printTo(out);
        summary.printFailuresTo(out, 30);
        out.printf(
                "ISOLATED-SUMMARY found=%d succeeded=%d failed=%d%n",
                summary.getTestsFoundCount(),
                summary.getTestsSucceededCount(),
                summary.getTotalFailureCount());
        System.exit(
                summary.getTotalFailureCount() == 0 && summary.getTestsFoundCount() > 0 ? 0 : 1);
    }
}
