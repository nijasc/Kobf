package dev.kobf.core

import org.objectweb.asm.ClassWriter

class PoolClassWriter(
    flags: Int,
    private val hierarchy: ClassHierarchy
) : ClassWriter(flags) {

    override fun getCommonSuperClass(type1: String, type2: String): String =
        hierarchy.commonSuperClass(type1, type2)
}