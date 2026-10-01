package dev.kobf.core

import java.io.File

data class ObfResult(
    val classCount: Int,
    val resourceCount: Int,
    val appliedTransforms: List<String>
)

class Obfuscator(private val transforms: List<Transformation>) {

    fun run(
        input: File,
        output: File,
        classpath: List<File> = emptyList(),
        keep: (String, String?) -> Boolean = { _, _ -> false }
    ): ObfResult {
        val pool = ClassPool()
        JarIo.read(input, pool)

        val ctx = TransformContext(keep)
        for (t in transforms) t.apply(pool, ctx)

        val flags = transforms.fold(0) { acc, t -> acc or t.writerFlags }
        val hierarchy = ClassHierarchy(pool, classpath)

        JarIo.write(output, pool) { name ->
            val node = pool.classes.getValue(name)
            val writer = PoolClassWriter(flags, hierarchy)
            node.accept(writer)
            writer.toByteArray()
        }

        return ObfResult(
            classCount = pool.classes.size,
            resourceCount = pool.resources.size,
            appliedTransforms = transforms.map { it.name }
        )
    }
}