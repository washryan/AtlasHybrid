package dev.atlashybrid.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class CompatibilityCollectorTest {
    @Test
    void recordsSupportedAndUnsupportedCallsWithStableMessage() {
        CompatibilityCollector collector = new CompatibilityCollector(Logger.getAnonymousLogger());
        collector.supported("ExamplePlugin");
        CompatibilityException exception = collector.unsupported("ExamplePlugin", "org.bukkit.entity.Player#someMethod", "bukkit-player", CompatibilityStatus.NOT_IMPLEMENTED);
        assertEquals(1, collector.supportedCalls("ExamplePlugin"));
        assertEquals(1, collector.unsupportedCalls("ExamplePlugin"));
        assertTrue(exception.getMessage().contains("Status: NOT_IMPLEMENTED"));
    }

    @Test
    void reportsLinkageFailureWithPluginSymbolAndRuntimeWhilePreservingFailure() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        List<String> messages = new ArrayList<>();
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { messages.add(record.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        CompatibilityCollector collector = new CompatibilityCollector(logger, "0.1.0-alpha");
        CompatibilityRuntime.install(collector);
        NoSuchMethodError original = new NoSuchMethodError("'void example.Plugin.saveConfig()'");
        try (CompatibilityRuntime.Scope ignored = CompatibilityRuntime.enter("ExamplePlugin")) {
            assertTrue(CompatibilityRuntime.reportLinkageFailure(original));
        } finally {
            CompatibilityRuntime.clear();
        }
        assertEquals(1, collector.unsupportedCalls("ExamplePlugin"));
        assertTrue(messages.get(0).contains("Missing API: example.Plugin#saveConfig()"));
        assertTrue(messages.get(0).contains("Runtime: 0.1.0-alpha"));
        assertEquals("'void example.Plugin.saveConfig()'", original.getMessage());
    }

    @Test
    void distinguishesBootstrapPhaseFromMissingApi() {
        CompatibilityCollector collector = new CompatibilityCollector(Logger.getAnonymousLogger());
        CompatibilityRuntime.install(collector);
        try (CompatibilityRuntime.Scope ignored = CompatibilityRuntime.enter("EarlyPlugin")) {
            CompatibilityRuntime.availableLater("JavaPlugin#getCommand", "CONSTRUCTION");
        } finally {
            CompatibilityRuntime.clear();
        }
        CompatibilityDiagnostic diagnostic = collector.diagnostics().get(0);
        assertEquals(CompatibilityStatus.AVAILABLE_LATER, diagnostic.status());
        assertEquals("plugin-bootstrap", diagnostic.module());
        assertTrue(diagnostic.detail().contains("PLUGIN_BOOTSTRAP_PHASE"));
        assertTrue(diagnostic.detail().contains("CONSTRUCTION"));
    }
}
