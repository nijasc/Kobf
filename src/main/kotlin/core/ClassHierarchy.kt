package dev.kobf.core

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.jar.JarFile

class ClassHierarchy(
    pool: ClassPool,
    classpath: List<File> = emptyList(),
    private val loader: ClassLoader = ClassHierarchy::class.java.classLoader
) {
    private class Info(val superName: String?, val interfaces: List<String>, val isInterface: Boolean)

    private val local = HashMap<String, Info>()
    private val reflectCache = HashMap<String, Info?>()

    init {
        for (cn in pool.classes.values) {
            local[cn.name] = Info(
                cn.superName,
                cn.interfaces?.toList() ?: emptyList(),
                cn.access and Opcodes.ACC_INTERFACE != 0
            )
        }
        for (jar in classpath) readJar(jar)
    }

    private fun readJar(file: File) {
        JarFile(file).use { jar ->
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                if (e.isDirectory || !e.name.endsWith(".class")) continue
                try {
                    val reader = ClassReader(jar.getInputStream(e).readBytes())
                    local.putIfAbsent(
                        reader.className,
                        Info(
                            reader.superName,
                            reader.interfaces.toList(),
                            reader.access and Opcodes.ACC_INTERFACE != 0
                        )
                    )
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun info(name: String): Info? {
        local[name]?.let { return it }
        if (name in reflectCache) return reflectCache[name]
        val resolved = try {
            val c = Class.forName(name.replace('/', '.'), false, loader)
            Info(
                c.superclass?.name?.replace('.', '/'),
                c.interfaces.map { it.name.replace('.', '/') },
                c.isInterface
            )
        } catch (_: Throwable) {
            null
        }
        reflectCache[name] = resolved
        return resolved
    }

    fun isInterface(name: String): Boolean = info(name)?.isInterface ?: false

    fun superName(name: String): String? = info(name)?.superName

    private fun allSupertypes(name: String): Set<String> {
        val out = LinkedHashSet<String>()
        val stack = ArrayDeque<String>()
        info(name)?.let { i ->
            i.superName?.let { stack.add(it) }
            stack.addAll(i.interfaces)
        }
        while (stack.isNotEmpty()) {
            val t = stack.removeLast()
            if (out.add(t)) {
                info(t)?.let { i ->
                    i.superName?.let { stack.add(it) }
                    stack.addAll(i.interfaces)
                }
            }
        }
        return out
    }

    fun isAssignableFrom(a: String, b: String): Boolean {
        if (a == b) return true
        if (a == "java/lang/Object") return true
        return a in allSupertypes(b)
    }

    fun commonSuperClass(type1: String, type2: String): String {
        if (type1 == type2) return type1
        if (isAssignableFrom(type1, type2)) return type1
        if (isAssignableFrom(type2, type1)) return type2
        if (isInterface(type1) || isInterface(type2)) return "java/lang/Object"
        var c = type1
        while (true) {
            c = superName(c) ?: return "java/lang/Object"
            if (isAssignableFrom(c, type2)) return c
        }
    }
}