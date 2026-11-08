package com.pzoptimizer.memory;

import com.pzoptimizer.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Vertical Level Pager & Pruner for 32-story structures in Build 42.
 * Equipped with multi-player co-op safety so floors near any player are never culled.
 */
public final class VerticalChunkPager {
    private static final int VERTICAL_ACTIVE_WINDOW = 6;
    private static long lastPruneTime = 0;
    private static final long PRUNE_INTERVAL_MS = 1000;

    private static Method getChunksMethod;
    private static Field minLevelField;
    private static Field maxLevelField;
    private static Method getRenderLevelsMethod;
    private static Method freeFBOsForLevelMethod;

    public static void onChunkMapUpdated(Object chunkMap) {
        if (chunkMap == null) return;

        long now = System.currentTimeMillis();
        if (now - lastPruneTime < PRUNE_INTERVAL_MS) {
            return;
        }
        lastPruneTime = now;

        try {
            Object[] chunks = getActiveChunks(chunkMap);
            if (chunks == null) return;

            int prunedLevels = 0;
            for (Object chunk : chunks) {
                if (chunk == null) continue;
                prunedLevels += pruneChunkVerticalLevels(chunk);
            }

            if (prunedLevels > 0) {
                Log.info("VerticalPager swept " + prunedLevels + " inactive upper floor levels.");
            }
        } catch (Throwable t) {
            Log.error("Error in VerticalChunkPager", t);
        }
    }

    private static int pruneChunkVerticalLevels(Object chunk) throws Exception {
        initChunkFields(chunk);

        int minZ = minLevelField.getInt(chunk);
        int maxZ = maxLevelField.getInt(chunk);

        if (maxZ - minZ <= VERTICAL_ACTIVE_WINDOW) {
            return 0;
        }

        int pruned = 0;
        Object renderLevels = getRenderLevelsMethod.invoke(chunk, 0);

        if (renderLevels != null) {
            for (int z = minZ; z <= maxZ; z += 2) {
                if (!FBOCacheManager.isLevelNearAnyPlayer(z, VERTICAL_ACTIVE_WINDOW)) {
                    if (freeFBOsForLevelMethod == null) {
                        freeFBOsForLevelMethod = renderLevels.getClass().getMethod("freeFBOsForLevel", int.class);
                        freeFBOsForLevelMethod.setAccessible(true);
                    }
                    freeFBOsForLevelMethod.invoke(renderLevels, z);
                    pruned++;
                }
            }
        }

        return pruned;
    }

    private static Object[] getActiveChunks(Object chunkMap) throws Exception {
        if (getChunksMethod == null) {
            getChunksMethod = chunkMap.getClass().getMethod("getChunks");
            getChunksMethod.setAccessible(true);
        }
        return (Object[]) getChunksMethod.invoke(chunkMap);
    }

    private static void initChunkFields(Object chunk) throws Exception {
        if (minLevelField == null) {
            minLevelField = chunk.getClass().getField("minLevel");
            maxLevelField = chunk.getClass().getField("maxLevel");
            getRenderLevelsMethod = chunk.getClass().getMethod("getRenderLevels", int.class);

            minLevelField.setAccessible(true);
            maxLevelField.setAccessible(true);
            getRenderLevelsMethod.setAccessible(true);
        }
    }
}
