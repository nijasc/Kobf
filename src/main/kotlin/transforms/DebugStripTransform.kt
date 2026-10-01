package dev.kobf.transforms

import dev.kobf.core.ClassPool
import dev.kobf.core.TransformContext
import dev.kobf.core.Transformation
import org.objectweb.asm.tree.LineNumberNode

class DebugStripTransform : Transformation {

    override val name = "strip-debug"

    override fun apply(pool: ClassPool, context: TransformContext) {
        for (cn in pool.classes.values) {
            cn.sourceFile = null
            cn.sourceDebug = null
            for (m in cn.methods) {
                m.localVariables = null
                m.parameters = null
                val insns = m.instructions ?: continue
                insns.toArray().filterIsInstance<LineNumberNode>().forEach { insns.remove(it) }
            }
        }
    }
}