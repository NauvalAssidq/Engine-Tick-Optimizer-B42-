package com.pzoptimizer.crowd;

import com.pzoptimizer.util.Log;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Crowd Pathfinding Budgeter and Swarm Flow Coordinator.
 * Equipped with door-thumping and close-proximity bypass guardrails.
 */
public final class CrowdFlowFieldManager {
    private static final int MAX_PATH_REQUESTS_PER_TICK = 8;
    private static final AtomicInteger pathRequestsThisTick = new AtomicInteger(0);

    private static Field repathDelayField;
    private static Field thumpFlagField;

    public static void onFrameStart() {
        pathRequestsThisTick.set(0);
    }

    public static boolean shouldAllowPathRequest(Object zombie, Object target) {
        if (zombie == null) return true;

        // Guardrail: Never defer zombies actively thumping doors or windows
        if (isThumping(zombie)) {
            return true;
        }

        // If quota exceeded this tick, throttle and defer
        if (pathRequestsThisTick.incrementAndGet() > MAX_PATH_REQUESTS_PER_TICK) {
            deferZombieRepath(zombie, 2.0f + (float) (Math.random() * 3.0));
            return false;
        }

        return true;
    }

    public static boolean shouldAllowLocationPath(Object zombie, float x, float y, float z) {
        if (zombie == null) return true;

        if (isThumping(zombie)) {
            return true;
        }

        if (pathRequestsThisTick.incrementAndGet() > MAX_PATH_REQUESTS_PER_TICK) {
            deferZombieRepath(zombie, 3.0f + (float) (Math.random() * 3.0));
            return false;
        }

        return true;
    }

    private static boolean isThumping(Object zombie) {
        try {
            if (thumpFlagField == null) {
                thumpFlagField = zombie.getClass().getField("thumpFlag");
                thumpFlagField.setAccessible(true);
            }
            return thumpFlagField.getInt(zombie) != 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void deferZombieRepath(Object zombie, float delay) {
        try {
            if (repathDelayField == null) {
                repathDelayField = zombie.getClass().getField("allowRepathDelay");
                repathDelayField.setAccessible(true);
            }
            float current = repathDelayField.getFloat(zombie);
            if (current < delay) {
                repathDelayField.setFloat(zombie, delay);
            }
        } catch (Throwable ignored) {
        }
    }
}
