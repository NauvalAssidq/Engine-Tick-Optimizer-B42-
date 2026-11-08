package com.pzoptimizer.memory;

import com.pzoptimizer.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * LRU Cache and vertical distance eviction manager with ground/player floor protection.
 * Prevents black missing chunk tiles when zoomed out while still capping vertical high-rise VRAM.
 */
public final class FBOCacheManager {
    private static final int MAX_ACTIVE_FBOS = Integer.getInteger("com.pzoptimizer.fbo.max", 160);
    private static final int MAX_VERTICAL_DELTA = Integer.getInteger("com.pzoptimizer.fbo.verticalDelta", 3);
    private static long currentFrame = 0;
    private static long lastTelemetryTime = 0;

    private static final class FBOEntry {
        final Object levels;
        final Object chunk;
        long lastUsedFrame;

        FBOEntry(Object levels, Object chunk, long frame) {
            this.levels = levels;
            this.chunk = chunk;
            this.lastUsedFrame = frame;
        }
    }

    private static final Map<Integer, FBOEntry> activeFBOs = new HashMap<>();
    private static final ArrayDeque<Integer> lruQueue = new ArrayDeque<>();

    private static Field chunkIndexField;
    private static Field fboMinLevelField;
    private static Method freeFBOMethod;
    private static Method freeFBOsForLevelMethod;
    private static Method getMinLevelMethod;
    private static Method getMaxLevelMethod;

    private static Field playersField;
    private static Method getZMethod;

    public static void onFrameStart() {
        currentFrame++;
        long now = System.currentTimeMillis();
        if (now - lastTelemetryTime > 15000) {
            lastTelemetryTime = now;
            synchronized (activeFBOs) {
                Log.info("[TELEMETRY] Frame " + currentFrame + " | Resident FBO Textures: " + activeFBOs.size() + "/" + MAX_ACTIVE_FBOS);
            }
        }
    }

    public static void onFBOUsed(Object levels, Object chunk) {
        if (levels == null || chunk == null) return;

        try {
            int index = getChunkIndex(chunk);
            synchronized (activeFBOs) {
                FBOEntry entry = activeFBOs.get(index);
                if (entry == null) {
                    entry = new FBOEntry(levels, chunk, currentFrame);
                    activeFBOs.put(index, entry);
                    lruQueue.addLast(index);
                } else {
                    entry.lastUsedFrame = currentFrame;
                }

                if (activeFBOs.size() > MAX_ACTIVE_FBOS) {
                    evictOldestNonPlayerFloor();
                }
            }
        } catch (Throwable t) {
            Log.error("Error in onFBOUsed", t);
        }
    }

    public static void sweepVertical(Object levels) {
        if (levels == null) return;

        try {
            int minZ = getMinLevel(levels);
            int maxZ = getMaxLevel(levels);

            for (int z = minZ; z <= maxZ; z += 2) {
                // Multi-player / Split-screen guard: only evict if level is far from ALL active players
                if (!isLevelNearAnyPlayer(z, MAX_VERTICAL_DELTA)) {
                    freeFBOsForLevel(levels, z);
                }
            }
        } catch (Throwable t) {
            Log.error("Error in sweepVertical", t);
        }
    }

    public static boolean isLevelNearAnyPlayer(int level, int maxDelta) {
        try {
            initPlayerFields();
            Object[] players = (Object[]) playersField.get(null);
            if (players == null) return true;

            for (Object player : players) {
                if (player != null) {
                    float pZ = (float) getZMethod.invoke(player);
                    if (Math.abs(level - (int) pZ) <= maxDelta) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            return true;
        }
        return false;
    }

    private static void evictOldestNonPlayerFloor() {
        int queueSize = lruQueue.size();
        for (int i = 0; i < queueSize; i++) {
            Integer candidateIndex = lruQueue.pollFirst();
            if (candidateIndex == null) break;

            FBOEntry entry = activeFBOs.get(candidateIndex);
            if (entry == null) continue;

            if (isPlayerFloorFBO(entry.chunk)) {
                lruQueue.addLast(candidateIndex);
                continue;
            }

            activeFBOs.remove(candidateIndex);
            if (entry.chunk != null && entry.levels != null) {
                try {
                    freeFBO(entry.levels, entry.chunk);
                } catch (Throwable ignored) {
                }
                break;
            }
        }
    }

    private static boolean isPlayerFloorFBO(Object chunk) {
        try {
            if (fboMinLevelField == null) {
                fboMinLevelField = chunk.getClass().getField("minLevel");
                fboMinLevelField.setAccessible(true);
            }
            int minLevel = fboMinLevelField.getInt(chunk);
            return isLevelNearAnyPlayer(minLevel, 1);
        } catch (Throwable t) {
            return true;
        }
    }

    private static int getChunkIndex(Object chunk) throws Exception {
        if (chunkIndexField == null) {
            chunkIndexField = chunk.getClass().getField("index");
            chunkIndexField.setAccessible(true);
        }
        return chunkIndexField.getInt(chunk);
    }

    private static int getMinLevel(Object levels) throws Exception {
        if (getMinLevelMethod == null) {
            getMinLevelMethod = levels.getClass().getMethod("getMinLevel", int.class);
            getMinLevelMethod.setAccessible(true);
        }
        return (int) getMinLevelMethod.invoke(levels, 0);
    }

    private static int getMaxLevel(Object levels) throws Exception {
        if (getMaxLevelMethod == null) {
            getMaxLevelMethod = levels.getClass().getMethod("getMaxLevel", int.class);
            getMaxLevelMethod.setAccessible(true);
        }
        return (int) getMaxLevelMethod.invoke(levels, 0);
    }

    private static void freeFBO(Object levels, Object chunk) throws Exception {
        if (freeFBOMethod == null) {
            Class<?> chunkClass = Class.forName("zombie.iso.fboRenderChunk.FBORenderChunk");
            freeFBOMethod = levels.getClass().getMethod("freeFBO", chunkClass);
            freeFBOMethod.setAccessible(true);
        }
        freeFBOMethod.invoke(levels, chunk);
    }

    private static void freeFBOsForLevel(Object levels, int level) throws Exception {
        if (freeFBOsForLevelMethod == null) {
            freeFBOsForLevelMethod = levels.getClass().getMethod("freeFBOsForLevel", int.class);
            freeFBOsForLevelMethod.setAccessible(true);
        }
        freeFBOsForLevelMethod.invoke(levels, level);
    }

    private static void initPlayerFields() throws Exception {
        if (playersField == null) {
            Class<?> isoPlayer = Class.forName("zombie.characters.IsoPlayer");
            playersField = isoPlayer.getField("players");
            playersField.setAccessible(true);
            getZMethod = isoPlayer.getMethod("getZ");
            getZMethod.setAccessible(true);
        }
    }

    public static int getActiveCount() {
        synchronized (activeFBOs) {
            return activeFBOs.size();
        }
    }
}
