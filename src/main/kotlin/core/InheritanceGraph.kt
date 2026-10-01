package dev.kobf.core

class InheritanceGraph(private val pool: ClassPool) {

    private val superName = HashMap<String, String?>()
    private val interfaces = HashMap<String, List<String>>()
    private val methodDecls = HashMap<String, Set<String>>()
    private val fieldDecls = HashMap<String, Set<String>>()
    private val supersCache = HashMap<String, Set<String>>()

    fun build() {
        for (cn in pool.classes.values) {
            superName[cn.name] = cn.superName
            interfaces[cn.name] = cn.interfaces?.toList() ?: emptyList()
            methodDecls[cn.name] = cn.methods.mapTo(HashSet()) { it.name + it.desc }
            fieldDecls[cn.name] = cn.fields.mapTo(HashSet()) { it.name + "|" + it.desc }
        }
    }

    fun inPool(name: String) = pool.contains(name)

    fun directSupers(name: String): List<String> {
        val out = ArrayList<String>()
        superName[name]?.let { out.add(it) }
        interfaces[name]?.let { out.addAll(it) }
        return out
    }

    fun allSupers(name: String): Set<String> {
        supersCache[name]?.let { return it }
        val out = LinkedHashSet<String>()
        val stack = ArrayDeque(directSupers(name))
        while (stack.isNotEmpty()) {
            val s = stack.removeLast()
            if (out.add(s) && pool.contains(s)) stack.addAll(directSupers(s))
        }
        supersCache[name] = out
        return out
    }

    fun declaresMethod(owner: String, name: String, desc: String): Boolean =
        methodDecls[owner]?.contains(name + desc) == true

    fun declaresField(owner: String, name: String, desc: String): Boolean =
        fieldDecls[owner]?.contains("$name|$desc") == true
}