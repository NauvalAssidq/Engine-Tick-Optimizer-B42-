package com.pzoptimizer.transformer;

import com.pzoptimizer.util.Log;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * Main ClassFileTransformer dispatching bytecode transformations.
 */
public final class PZClassTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        if (className == null) {
            return null;
        }

        try {
            if ("zombie/iso/fboRenderChunk/FBORenderLevels".equals(className)) {
                return FBORenderTransformer.transformFBORenderLevels(classfileBuffer);
            } else if ("zombie/iso/fboRenderChunk/FBORenderChunkManager".equals(className)) {
                return FBORenderTransformer.transformFBORenderChunkManager(classfileBuffer);
            } else if ("zombie/characters/IsoZombie".equals(className)) {
                return IsoZombieTransformer.transformIsoZombie(classfileBuffer);
            } else if ("zombie/iso/IsoChunkMap".equals(className)) {
                return IsoChunkMapTransformer.transformIsoChunkMap(classfileBuffer);
            }
        } catch (Throwable t) {
            Log.error("Error transforming class " + className, t);
        }

        return null;
    }
}
