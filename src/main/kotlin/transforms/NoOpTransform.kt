package dev.kobf.transforms

import dev.kobf.core.ClassPool
import dev.kobf.core.TransformContext
import dev.kobf.core.Transformation

class NoOpTransform : Transformation {
    override val name = "noop"
    override fun apply(pool: ClassPool, context: TransformContext) {}
}