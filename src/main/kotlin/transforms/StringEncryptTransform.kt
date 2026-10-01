package dev.kobf.transforms

import dev.kobf.core.ClassPool
import dev.kobf.core.TransformContext
import dev.kobf.core.Transformation
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FrameNode
import org.objectweb.asm.tree.IincInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import org.objectweb.asm.tree.ClassNode
import java.util.Random

class StringEncryptTransform(
    seed: Long? = null,
    private val contextBound: Boolean = false
) : Transformation {

    override val name = if (contextBound) "string-encrypt-ctx" else "string-encrypt"
    override val writerFlags = ClassWriter.COMPUTE_MAXS

    private val rng = if (seed == null) Random() else Random(seed)
    private val decryptDesc = "(Ljava/lang/String;I)Ljava/lang/String;"

    var encryptedCount = 0
        private set

    override fun apply(pool: ClassPool, context: TransformContext) {
        for (cn in pool.classes.values) {
            if (isSkippable(cn)) continue
            processClass(cn)
        }
    }

    private fun isSkippable(cn: ClassNode): Boolean {
        if (cn.name == "module-info") return true
        if (cn.access and Opcodes.ACC_INTERFACE != 0) return true
        if (cn.access and Opcodes.ACC_MODULE != 0) return true
        return false
    }

    private fun processClass(cn: ClassNode) {
        val methods = cn.methods.toList()
        var touched = false

        for (method in methods) {
            val insns = method.instructions ?: continue
            val ldcs = insns.toArray().filterIsInstance<LdcInsnNode>()
                .filter { it.cst is String && (it.cst as String).isNotEmpty() }

            if (ldcs.isEmpty()) continue
            touched = true

            for (ldc in ldcs) {
                val plain = ldc.cst as String
                val realKey = rng.nextInt()
                ldc.cst = encrypt(plain, realKey)

                val emittedKey =
                    if (contextBound) realKey xor contextHash(cn.name, method.name) else realKey

                val call = InsnList().apply {
                    add(pushInt(emittedKey))
                    add(MethodInsnNode(Opcodes.INVOKESTATIC, cn.name, DECRYPT_NAME, decryptDesc, false))
                }
                insns.insert(ldc, call)
                encryptedCount++
            }
        }

        if (touched && cn.methods.none { it.name == DECRYPT_NAME && it.desc == decryptDesc }) {
            cn.methods.add(if (contextBound) buildContextDecrypt() else buildPlainDecrypt())
        }
    }

    private fun contextHash(owner: String, methodName: String): Int =
        (owner.replace('/', '.') + methodName).hashCode()

    private fun encrypt(s: String, key: Int): String {
        val sb = StringBuilder(s.length * 4)
        for (i in s.indices) {
            val v = (s[i].code xor (key + i)) and 0xFFFF
            sb.append("%04x".format(v))
        }
        return sb.toString()
    }

    private fun pushInt(value: Int): AbstractInsnNode = when (value) {
        in -1..5 -> InsnNode(Opcodes.ICONST_0 + value)
        in Byte.MIN_VALUE..Byte.MAX_VALUE -> IntInsnNode(Opcodes.BIPUSH, value)
        in Short.MIN_VALUE..Short.MAX_VALUE -> IntInsnNode(Opcodes.SIPUSH, value)
        else -> LdcInsnNode(value)
    }

    private fun buildPlainDecrypt(): MethodNode {
        val m = MethodNode(
            Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC,
            DECRYPT_NAME, decryptDesc, null, null
        )
        val l = m.instructions
        val loop = LabelNode()
        val end = LabelNode()
        val frameLocals = arrayOf<Any>(
            "java/lang/String", Opcodes.INTEGER, Opcodes.INTEGER, "[C", Opcodes.INTEGER
        )

        l.add(VarInsnNode(Opcodes.ALOAD, 0))
        l.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false))
        l.add(InsnNode(Opcodes.ICONST_4))
        l.add(InsnNode(Opcodes.IDIV))
        l.add(VarInsnNode(Opcodes.ISTORE, 2))
        l.add(VarInsnNode(Opcodes.ILOAD, 2))
        l.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_CHAR))
        l.add(VarInsnNode(Opcodes.ASTORE, 3))
        l.add(InsnNode(Opcodes.ICONST_0))
        l.add(VarInsnNode(Opcodes.ISTORE, 4))

        l.add(loop)
        l.add(FrameNode(Opcodes.F_FULL, 5, frameLocals, 0, emptyArray()))
        l.add(VarInsnNode(Opcodes.ILOAD, 4))
        l.add(VarInsnNode(Opcodes.ILOAD, 2))
        l.add(JumpInsnNode(Opcodes.IF_ICMPGE, end))

        l.add(VarInsnNode(Opcodes.ALOAD, 3))
        l.add(VarInsnNode(Opcodes.ILOAD, 4))
        l.add(VarInsnNode(Opcodes.ALOAD, 0))
        l.add(VarInsnNode(Opcodes.ILOAD, 4))
        l.add(InsnNode(Opcodes.ICONST_4))
        l.add(InsnNode(Opcodes.IMUL))
        l.add(VarInsnNode(Opcodes.ILOAD, 4))
        l.add(InsnNode(Opcodes.ICONST_4))
        l.add(InsnNode(Opcodes.IMUL))
        l.add(InsnNode(Opcodes.ICONST_4))
        l.add(InsnNode(Opcodes.IADD))
        l.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false))
        l.add(IntInsnNode(Opcodes.BIPUSH, 16))
        l.add(MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;I)I", false))
        l.add(VarInsnNode(Opcodes.ILOAD, 1))
        l.add(VarInsnNode(Opcodes.ILOAD, 4))
        l.add(InsnNode(Opcodes.IADD))
        l.add(InsnNode(Opcodes.IXOR))
        l.add(InsnNode(Opcodes.I2C))
        l.add(InsnNode(Opcodes.CASTORE))
        l.add(IincInsnNode(4, 1))
        l.add(JumpInsnNode(Opcodes.GOTO, loop))

        l.add(end)
        l.add(FrameNode(Opcodes.F_FULL, 5, frameLocals, 0, emptyArray()))
        l.add(TypeInsnNode(Opcodes.NEW, "java/lang/String"))
        l.add(InsnNode(Opcodes.DUP))
        l.add(VarInsnNode(Opcodes.ALOAD, 3))
        l.add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/String", "<init>", "([C)V", false))
        l.add(InsnNode(Opcodes.ARETURN))
        return m
    }

    private fun buildContextDecrypt(): MethodNode {
        val m = MethodNode(
            Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC,
            DECRYPT_NAME, decryptDesc, null, null
        )
        val l = m.instructions
        val loop = LabelNode()
        val end = LabelNode()
        val frameLocals = arrayOf<Any>(
            "java/lang/String", Opcodes.INTEGER, Opcodes.INTEGER, Opcodes.INTEGER, "[C", Opcodes.INTEGER
        )

        l.add(TypeInsnNode(Opcodes.NEW, "java/lang/Throwable"))
        l.add(InsnNode(Opcodes.DUP))
        l.add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Throwable", "<init>", "()V", false))
        l.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable", "getStackTrace", "()[Ljava/lang/StackTraceElement;", false))
        l.add(InsnNode(Opcodes.ICONST_1))
        l.add(InsnNode(Opcodes.AALOAD))
        l.add(VarInsnNode(Opcodes.ASTORE, 6))

        l.add(TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"))
        l.add(InsnNode(Opcodes.DUP))
        l.add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false))
        l.add(VarInsnNode(Opcodes.ALOAD, 6))
        l.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StackTraceElement", "getClassName", "()Ljava/lang/String;", false))
        l.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false))
        l.add(VarInsnNode(Opcodes.ALOAD, 6))
        l.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StackTraceElement", "getMethodName", "()Ljava/lang/String;", false))
        l.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false))
        l.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false))
        l.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false))
        l.add(VarInsnNode(Opcodes.ILOAD, 1))
        l.add(InsnNode(Opcodes.IXOR))
        l.add(VarInsnNode(Opcodes.ISTORE, 2))

        l.add(VarInsnNode(Opcodes.ALOAD, 0))
        l.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false))
        l.add(InsnNode(Opcodes.ICONST_4))
        l.add(InsnNode(Opcodes.IDIV))
        l.add(VarInsnNode(Opcodes.ISTORE, 3))
        l.add(VarInsnNode(Opcodes.ILOAD, 3))
        l.add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_CHAR))
        l.add(VarInsnNode(Opcodes.ASTORE, 4))
        l.add(InsnNode(Opcodes.ICONST_0))
        l.add(VarInsnNode(Opcodes.ISTORE, 5))

        l.add(loop)
        l.add(FrameNode(Opcodes.F_FULL, 6, frameLocals, 0, emptyArray()))
        l.add(VarInsnNode(Opcodes.ILOAD, 5))
        l.add(VarInsnNode(Opcodes.ILOAD, 3))
        l.add(JumpInsnNode(Opcodes.IF_ICMPGE, end))

        l.add(VarInsnNode(Opcodes.ALOAD, 4))
        l.add(VarInsnNode(Opcodes.ILOAD, 5))
        l.add(VarInsnNode(Opcodes.ALOAD, 0))
        l.add(VarInsnNode(Opcodes.ILOAD, 5))
        l.add(InsnNode(Opcodes.ICONST_4))
        l.add(InsnNode(Opcodes.IMUL))
        l.add(VarInsnNode(Opcodes.ILOAD, 5))
        l.add(InsnNode(Opcodes.ICONST_4))
        l.add(InsnNode(Opcodes.IMUL))
        l.add(InsnNode(Opcodes.ICONST_4))
        l.add(InsnNode(Opcodes.IADD))
        l.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false))
        l.add(IntInsnNode(Opcodes.BIPUSH, 16))
        l.add(MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;I)I", false))
        l.add(VarInsnNode(Opcodes.ILOAD, 2))
        l.add(VarInsnNode(Opcodes.ILOAD, 5))
        l.add(InsnNode(Opcodes.IADD))
        l.add(InsnNode(Opcodes.IXOR))
        l.add(InsnNode(Opcodes.I2C))
        l.add(InsnNode(Opcodes.CASTORE))
        l.add(IincInsnNode(5, 1))
        l.add(JumpInsnNode(Opcodes.GOTO, loop))

        l.add(end)
        l.add(FrameNode(Opcodes.F_FULL, 6, frameLocals, 0, emptyArray()))
        l.add(TypeInsnNode(Opcodes.NEW, "java/lang/String"))
        l.add(InsnNode(Opcodes.DUP))
        l.add(VarInsnNode(Opcodes.ALOAD, 4))
        l.add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/String", "<init>", "([C)V", false))
        l.add(InsnNode(Opcodes.ARETURN))
        return m
    }

    companion object {
        private const val DECRYPT_NAME = "\$kobfDecrypt"
    }
}