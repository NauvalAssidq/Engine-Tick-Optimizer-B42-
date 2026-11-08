package com.pzoptimizer.transformer;

import com.pzoptimizer.util.Log;
import org.objectweb.asm.*;

public final class IsoZombieTransformer {

    public static byte[] transformIsoZombie(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);

            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    if ("update".equals(name) && "()V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                Label continueUpdate = new Label();

                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "com/pzoptimizer/crowd/ZombieLODManager",
                                    "shouldUpdateZombie",
                                    "(Ljava/lang/Object;)Z",
                                    false
                                );
                                super.visitJumpInsn(Opcodes.IFNE, continueUpdate);
                                super.visitInsn(Opcodes.RETURN);

                                super.visitLabel(continueUpdate);
                            }
                        };
                    }

                    if ("pathToCharacter".equals(name) && "(Lzombie/characters/IsoGameCharacter;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                Label continuePath = new Label();

                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitVarInsn(Opcodes.ALOAD, 1);
                                super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "com/pzoptimizer/crowd/CrowdFlowFieldManager",
                                    "shouldAllowPathRequest",
                                    "(Ljava/lang/Object;Ljava/lang/Object;)Z",
                                    false
                                );
                                super.visitJumpInsn(Opcodes.IFNE, continuePath);
                                super.visitInsn(Opcodes.RETURN);

                                super.visitLabel(continuePath);
                            }
                        };
                    }

                    if ("pathToLocationF".equals(name) && "(FFF)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                Label continuePath = new Label();

                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitVarInsn(Opcodes.FLOAD, 1);
                                super.visitVarInsn(Opcodes.FLOAD, 2);
                                super.visitVarInsn(Opcodes.FLOAD, 3);
                                super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "com/pzoptimizer/crowd/CrowdFlowFieldManager",
                                    "shouldAllowLocationPath",
                                    "(Ljava/lang/Object;FFF)Z",
                                    false
                                );
                                super.visitJumpInsn(Opcodes.IFNE, continuePath);
                                super.visitInsn(Opcodes.RETURN);

                                super.visitLabel(continuePath);
                            }
                        };
                    }

                    return mv;
                }
            };

            reader.accept(visitor, 0);
            Log.info("Successfully hooked IsoZombie.update() with 3-tier distance LOD.");
            return writer.toByteArray();
        } catch (Throwable t) {
            Log.error("Failed to transform IsoZombie", t);
            return classBytes;
        }
    }
}
