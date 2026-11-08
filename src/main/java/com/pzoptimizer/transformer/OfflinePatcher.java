package com.pzoptimizer.transformer;

import com.pzoptimizer.util.Log;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class OfflinePatcher {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: OfflinePatcher <gameDir> <outputClassesDir> [mode: standalone|companion]");
            System.exit(1);
        }

        File gameDir = new File(args[0]);
        File outputDir = new File(args[1]);
        String mode = args.length > 2 ? args[2].toLowerCase() : "standalone";
        File pzJar = new File(gameDir, "projectzomboid.jar");

        Log.info("Applying offline bytecode optimizations (mode: " + mode + ") from game files...");

        try {
            patchClass(gameDir, pzJar, outputDir, "zombie/iso/fboRenderChunk/FBORenderLevels.class",
                FBORenderTransformer::transformFBORenderLevels);

            patchClass(gameDir, pzJar, outputDir, "zombie/iso/fboRenderChunk/FBORenderChunkManager.class",
                FBORenderTransformer::transformFBORenderChunkManager);

            if (!"companion".equalsIgnoreCase(mode)) {
                patchClass(gameDir, pzJar, outputDir, "zombie/characters/IsoZombie.class",
                    IsoZombieTransformer::transformIsoZombie);

                patchClass(gameDir, pzJar, outputDir, "zombie/iso/IsoChunkMap.class",
                    IsoChunkMapTransformer::transformIsoChunkMap);
            } else {
                Log.info("Companion mode: omitting IsoZombie and IsoChunkMap to preserve PZ_Optimization integrity.");
            }

            Log.info("Offline bytecode patching completed successfully.");
        } catch (Throwable t) {
            Log.error("Error during offline patching", t);
            System.exit(1);
        }
    }

    private interface TransformerFn {
        byte[] transform(byte[] input);
    }

    private static void patchClass(File gameDir, File pzJar, File outputDir, String internalPath, TransformerFn fn) throws Exception {
        byte[] inputBytes = null;

        // Check if class exists as loose file on disk (e.g. from pzopt)
        File looseFile = new File(gameDir, internalPath.replace('/', File.separatorChar));
        if (looseFile.exists()) {
            Log.info("Reading loose class from disk: " + looseFile.getAbsolutePath());
            inputBytes = Files.readAllBytes(looseFile.toPath());
        } else {
            // Read from projectzomboid.jar
            Log.info("Reading class from projectzomboid.jar: " + internalPath);
            try (JarFile jar = new JarFile(pzJar)) {
                JarEntry entry = jar.getJarEntry(internalPath);
                if (entry == null) {
                    throw new RuntimeException("Entry not found in jar: " + internalPath);
                }
                try (InputStream is = jar.getInputStream(entry)) {
                    inputBytes = is.readAllBytes();
                }
            }
        }

        byte[] transformed = fn.transform(inputBytes);

        File targetFile = new File(outputDir, internalPath.replace('/', File.separatorChar));
        targetFile.getParentFile().mkdirs();
        Files.write(targetFile.toPath(), transformed);
        Log.info("Written transformed class: " + targetFile.getAbsolutePath());
    }
}
