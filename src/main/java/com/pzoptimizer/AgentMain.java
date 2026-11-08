package com.pzoptimizer;

import com.pzoptimizer.transformer.PZClassTransformer;
import com.pzoptimizer.util.Log;

import java.lang.instrument.Instrumentation;

/**
 * Engine Tick Optimizer (ETO) - Core Java Agent Entry Point.
 * Attached via -javaagent:engine-tick-optimizer.jar at JVM boot.
 */
public final class AgentMain {
    private static Instrumentation instrumentation;

    public static void premain(String agentArgs, Instrumentation inst) {
        initialize(agentArgs, inst);
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        initialize(agentArgs, inst);
    }

    private static void initialize(String agentArgs, Instrumentation inst) {
        instrumentation = inst;
        Log.info("=================================================");
        Log.info("Engine Tick Optimizer (ETO) v0.1.0-alpha initializing...");
        Log.info("Target: Project Zomboid Build 42 Architecture");
        Log.info("=================================================");

        try {
            PZClassTransformer transformer = new PZClassTransformer();
            inst.addTransformer(transformer, true);
            Log.info("Bytecode transformer registered successfully.");
        } catch (Throwable t) {
            Log.error("Failed to initialize bytecode transformer", t);
        }
    }

    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }
}
