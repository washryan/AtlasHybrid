package dev.atlashybrid.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
