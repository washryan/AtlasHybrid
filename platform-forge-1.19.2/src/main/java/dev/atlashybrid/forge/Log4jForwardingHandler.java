package dev.atlashybrid.forge;

import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.apache.logging.log4j.LogManager;

final class Log4jForwardingHandler extends Handler {
    @Override
    public void publish(LogRecord record) {
        if (record == null || !isLoggable(record)) return;
        org.apache.logging.log4j.Level level;
        int value = record.getLevel().intValue();
        if (value >= java.util.logging.Level.SEVERE.intValue()) level = org.apache.logging.log4j.Level.ERROR;
        else if (value >= java.util.logging.Level.WARNING.intValue()) level = org.apache.logging.log4j.Level.WARN;
        else if (value >= java.util.logging.Level.INFO.intValue()) level = org.apache.logging.log4j.Level.INFO;
        else if (value >= java.util.logging.Level.FINE.intValue()) level = org.apache.logging.log4j.Level.DEBUG;
        else level = org.apache.logging.log4j.Level.TRACE;
        LogManager.getLogger(record.getLoggerName()).log(level, record.getMessage(), record.getThrown());
    }

    @Override public void flush() { }
    @Override public void close() { }
}
