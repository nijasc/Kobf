package dev.kobf.core

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode

class ClassPool {
    val classes = LinkedHashMap<String, ClassNode>()
    val resources = LinkedHashMap<String, ByteArray>()

    fun add(bytes: ByteArray) {
        val node = ClassNode()
        ClassReader(bytes).accept(node, 0)
        classes[node.name] = node
    }

    fun addResource(path: String, bytes: ByteArray) {
        resources[path] = bytes
    }

    fun contains(internalName: String) = internalName in classes
}