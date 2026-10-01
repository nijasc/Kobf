package dev.kobf.core

interface Transformation {
    val name: String
    val writerFlags: Int get() = 0
    fun apply(pool: ClassPool, context: TransformContext)
}

class TransformContext(
    val keep: (owner: String, member: String?) -> Boolean
)