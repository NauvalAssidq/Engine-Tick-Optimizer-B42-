package com.pzoptimizer.util;

public final class Log {
    private static final String PREFIX = "[EngineTickOptimizer] ";

    public static void info(String message) {
        System.out.println(PREFIX + "[INFO] " + message);
    }

    public static void warn(String message) {
        System.out.println(PREFIX + "[WARN] " + message);
    }

    public static void error(String message) {
        System.err.println(PREFIX + "[ERROR] " + message);
    }

    public static void error(String message, Throwable t) {
        System.err.println(PREFIX + "[ERROR] " + message);
        t.printStackTrace(System.err);
    }
}
