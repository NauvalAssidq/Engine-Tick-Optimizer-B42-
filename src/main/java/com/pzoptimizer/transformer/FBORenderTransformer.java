package com.pzoptimizer.transformer;

import com.pzoptimizer.util.Log;
import org.objectweb.asm.*;

public final class FBORenderTransformer {

    public static byte[] transformFBORenderLevels(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);

            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    if ("getOrCreateFBOForLevel".equals(name) && "(IF)Lzombie/iso/fboRenderChunk/FBORenderChunk;".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.ARETURN) {
                                    super.visitInsn(Opcodes.DUP);
                                    super.visitVarInsn(Opcodes.ALOAD, 0);
                                    super.visitInsn(Opcodes.SWAP);
                                    super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "com/pzoptimizer/memory/FBOCacheManager",
                                        "onFBOUsed",
                                        "(Ljava/lang/Object;Ljava/lang/Object;)V",
                                        false
                                    );
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }

                    // Hook calculateOnScreen(int) -> sweep vertical floors
                    if ("calculateOnScreen".equals(name) && "(I)Z".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "com/pzoptimizer/memory/FBOCacheManager",
                                    "sweepVertical",
                                    "(Ljava/lang/Object;)V",
                                    false
                                );
                            }
                        };
                    }

                    return mv;
                }
            };

            reader.accept(visitor, 0);
            Log.info("Successfully hooked FBORenderLevels with FBO LRU & vertical sweep.");
            return writer.toByteArray();
        } catch (Throwable t) {
            Log.error("Failed to transform FBORenderLevels", t);
            return classBytes;
        }
    }

    public static byte[] transformFBORenderChunkManager(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);

            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // Hook startFrame() -> notify FBOCacheManager and ZombieLODManager of frame advance
                    if ("startFrame".equals(name) && "()V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "com/pzoptimizer/memory/FBOCacheManager",
                                    "onFrameStart",
                                    "()V",
                                    false
                                );
                                super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "com/pzoptimizer/crowd/ZombieLODManager",
                                    "onGameTick",
                                    "()V",
                                    false
                                );
                                super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "com/pzoptimizer/crowd/CrowdFlowFieldManager",
                                    "onFrameStart",
                                    "()V",
                                    false
                                );
                            }
                        };
                    }

                    return mv;
                }
            };

            reader.accept(visitor, 0);
            Log.info("Successfully hooked FBORenderChunkManager.startFrame().");
            return writer.toByteArray();
        } catch (Throwable t) {
            Log.error("Failed to transform FBORenderChunkManager", t);
            return classBytes;
        }
    }
}
