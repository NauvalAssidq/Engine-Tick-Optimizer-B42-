package com.pzoptimizer.crowd;

import com.pzoptimizer.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Three-tier distance Level of Detail (LOD) controller with physics and state guardrails.
 */
public final class ZombieLODManager {
    private static long tickCounter = 0;

    private static Field targetField;
    private static Field zombieIdField;
    private static Field staggerBackField;
    private static Field thumpFlagField;
    private static Method isKnockedDownMethod;
    private static Method isFallingMethod;
    private static Method getHitTimeMethod;
    private static Method getXMethod;
    private static Method getYMethod;

    private static Field playersField;
    private static Method getVehicleMethod;

    public static void onGameTick() {
        tickCounter++;
    }

    public static boolean shouldUpdateZombie(Object zombie) {
        if (zombie == null) return true;

        try {
            // Guardrail 1: Active Target (aggro/combat) -> full rate
            if (hasActiveTarget(zombie)) {
                return true;
            }

            // Guardrail 2: Physics, Stagger, Fall, Knockdown, or Hit reaction -> full rate
            if (isReactingOrPhysicallyActive(zombie)) {
                return true;
            }

            // Guardrail 3: Door / Window Thumping -> full rate
            if (isThumping(zombie)) {
                return true;
            }

            // Guardrail 4: Fast Vehicle proximity -> full rate if player is driving
            if (isAnyPlayerInVehicle()) {
                return true;
            }

            // Distance calculation to nearest player
            float zX = getZombieX(zombie);
            float zY = getZombieY(zombie);
            float minDistSq = getMinDistanceSqToPlayers(zX, zY);

            int id = getZombieId(zombie);

            // Tier 1: Near (< 16 tiles = 256 distSq) -> Full rate
            if (minDistSq < 256.0f) {
                return true;
            }

            // Tier 2: Mid (16-32 tiles = 1024 distSq) -> Tick every 2nd frame
            if (minDistSq < 1024.0f) {
                return ((tickCounter + id) & 1) == 0;
            }

            // Tier 3: Far (> 32 tiles) -> Tick every 5th frame
            return ((tickCounter + id) % 5) == 0;

        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean hasActiveTarget(Object zombie) throws Exception {
        if (targetField == null) {
            targetField = zombie.getClass().getField("target");
            targetField.setAccessible(true);
        }
        return targetField.get(zombie) != null;
    }

    private static boolean isReactingOrPhysicallyActive(Object zombie) {
        try {
            if (staggerBackField == null) {
                staggerBackField = zombie.getClass().getField("staggerBack");
                staggerBackField.setAccessible(true);
                isKnockedDownMethod = zombie.getClass().getMethod("isKnockedDown");
                isFallingMethod = zombie.getClass().getMethod("isFalling");
                getHitTimeMethod = zombie.getClass().getMethod("getHitTime");
                isKnockedDownMethod.setAccessible(true);
                isFallingMethod.setAccessible(true);
                getHitTimeMethod.setAccessible(true);
            }

            if (staggerBackField.getBoolean(zombie)) return true;
            if ((boolean) isKnockedDownMethod.invoke(zombie)) return true;
            if ((boolean) isFallingMethod.invoke(zombie)) return true;
            if ((int) getHitTimeMethod.invoke(zombie) > 0) return true;
        } catch (Throwable ignored) {
        }
        return false;
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

    private static boolean isAnyPlayerInVehicle() {
        try {
            initPlayerFields();
            Object[] players = (Object[]) playersField.get(null);
            if (players == null) return false;

            for (Object player : players) {
                if (player != null && getVehicleMethod.invoke(player) != null) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static float getMinDistanceSqToPlayers(float zX, float zY) {
        float minDistSq = Float.MAX_VALUE;
        try {
            initPlayerFields();
            Object[] players = (Object[]) playersField.get(null);
            if (players != null) {
                for (Object player : players) {
                    if (player != null) {
                        float pX = (float) getXMethod.invoke(player);
                        float pY = (float) getYMethod.invoke(player);
                        float dx = zX - pX;
                        float dy = zY - pY;
                        float distSq = dx * dx + dy * dy;
                        if (distSq < minDistSq) {
                            minDistSq = distSq;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return minDistSq == Float.MAX_VALUE ? 0.0f : minDistSq;
    }

    private static int getZombieId(Object zombie) throws Exception {
        if (zombieIdField == null) {
            try {
                zombieIdField = zombie.getClass().getField("zombieId");
                zombieIdField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                return System.identityHashCode(zombie);
            }
        }
        return zombieIdField.getInt(zombie);
    }

    private static float getZombieX(Object zombie) throws Exception {
        if (getXMethod == null) {
            getXMethod = zombie.getClass().getMethod("getX");
            getXMethod.setAccessible(true);
        }
        return (float) getXMethod.invoke(zombie);
    }

    private static float getZombieY(Object zombie) throws Exception {
        if (getYMethod == null) {
            getYMethod = zombie.getClass().getMethod("getY");
            getYMethod.setAccessible(true);
        }
        return (float) getYMethod.invoke(zombie);
    }

    private static void initPlayerFields() throws Exception {
        if (playersField == null) {
            Class<?> isoPlayer = Class.forName("zombie.characters.IsoPlayer");
            playersField = isoPlayer.getField("players");
            playersField.setAccessible(true);
            getVehicleMethod = isoPlayer.getMethod("getVehicle");
            getVehicleMethod.setAccessible(true);
        }
    }
}
