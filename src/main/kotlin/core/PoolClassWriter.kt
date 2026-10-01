package dev.kobf.core

import org.objectweb.asm.ClassWriter

class PoolClassWriter(
    flags: Int,
    private val pool: ClassPool
) : ClassWriter(flags) {

    override fun getCommonSuperClass(type1: String, type2: String): String {
        return try {
            super.getCommonSuperClass(type1, type2)
        } catch (_: Throwable) {
            if (pool.contains(type1) || pool.contains(type2)) "java/lang/Object"
            else throw RuntimeException("Type cannot be resolved: $type1 / $type2")
        }
    }
}