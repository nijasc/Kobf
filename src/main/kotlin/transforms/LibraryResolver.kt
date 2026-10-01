package dev.kobf.transforms

import org.objectweb.asm.Type
import java.lang.reflect.Modifier

class LibraryResolver(private val loader: ClassLoader) {

    private val cache = HashMap<String, Set<String>?>()

    private val objectMethods = setOf(
        "equals(Ljava/lang/Object;)Z",
        "hashCode()I",
        "toString()Ljava/lang/String;",
        "clone()Ljava/lang/Object;",
        "finalize()V"
    )

    fun isObjectMethod(name: String, desc: String) = (name + desc) in objectMethods

    fun signatures(internalName: String): Set<String>? {
        if (internalName in cache) return cache[internalName]
        val result = try {
            val c = Class.forName(internalName.replace('/', '.'), false, loader)
            val set = HashSet<String>()
            for (m in c.methods) set.add(m.name + Type.getMethodDescriptor(m))
            var k: Class<*>? = c
            while (k != null) {
                for (m in k.declaredMethods) {
                    if (!Modifier.isStatic(m.modifiers) && !Modifier.isPrivate(m.modifiers)) {
                        set.add(m.name + Type.getMethodDescriptor(m))
                    }
                }
                k = k.superclass
            }
            set
        } catch (_: Throwable) {
            null
        }
        cache[internalName] = result
        return result
    }
}