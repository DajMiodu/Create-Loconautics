package com.lycoris.loconautics.foundation.debug;

import com.lycoris.loconautics.config.DiagnosticsConfig;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;

public final class DebugLogFilter extends AbstractFilter {

    private static final String PACKAGE = "com.lycoris.loconautics";

    private DebugLogFilter() {
    }

    public static void install() {
        LoggerContext context = (LoggerContext) LogManager.getContext(DebugLogFilter.class.getClassLoader(), false);
        Configuration config = context.getConfiguration();
        LoggerConfig own = config.getLoggerConfig(PACKAGE);
        if (!own.getName().equals(PACKAGE)) {
            own = new LoggerConfig(PACKAGE, config.getRootLogger().getLevel(), true);
            config.addLogger(PACKAGE, own);
        }
        own.addFilter(new DebugLogFilter());
        context.updateLoggers();
    }

    @Override
    public Result filter(LogEvent event) {
        String name = event.getLoggerName();
        if (name == null || !name.startsWith(PACKAGE) || event.getLevel().isMoreSpecificThan(Level.WARN)) {
            return Result.NEUTRAL;
        }
        return DiagnosticsConfig.debugOutput() ? Result.NEUTRAL : Result.DENY;
    }
}
