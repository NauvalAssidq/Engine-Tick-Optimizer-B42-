package com.pzoptimizer.transformer;

import com.pzoptimizer.util.Log;
import org.objectweb.asm.*;

public final class IsoChunkMapTransformer {

    public static byte[] transformIsoChunkMap(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);

            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // Hook calculateZExtentsForChunkMap() -> trigger vertical level pruner
                    if ("calculateZExtentsForChunkMap".equals(name) && "()V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    super.visitVarInsn(Opcodes.ALOAD, 0);
                                    super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "com/pzoptimizer/memory/VerticalChunkPager",
                                        "onChunkMapUpdated",
                                        "(Ljava/lang/Object;)V",
                                        false
                                    );
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }

                    return mv;
                }
            };

            reader.accept(visitor, 0);
            Log.info("Successfully hooked IsoChunkMap.calculateZExtentsForChunkMap() with VerticalChunkPager.");
            return writer.toByteArray();
        } catch (Throwable t) {
            Log.error("Failed to transform IsoChunkMap", t);
            return classBytes;
        }
    }
}
