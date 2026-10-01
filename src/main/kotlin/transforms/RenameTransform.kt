package dev.kobf.transforms

import dev.kobf.core.ClassPool
import dev.kobf.core.InheritanceGraph
import dev.kobf.core.TransformContext
import dev.kobf.core.Transformation
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

class RenameTransform : Transformation {

    override val name = "rename"
    override val writerFlags = ClassWriter.COMPUTE_MAXS

    private val classMap = HashMap<String, String>()
    private val methodMap = HashMap<String, String>()
    private val fieldMap = HashMap<String, String>()

    private val classNames = NameGenerator()
    private val methodNames = NameGenerator()
    private val fieldNames = NameGenerator()

    override fun apply(pool: ClassPool, context: TransformContext) {
        val graph = InheritanceGraph(pool).apply { build() }
        val resolver = LibraryResolver(javaClass.classLoader)

        stripKotlinMetadata(pool)

        val keptClasses = collectKeptClasses(pool)
        computeClassMap(pool, keptClasses)
        computeFieldMap(pool, graph)
        computeMethodMap(pool, graph, resolver)

        val remapper = HierarchyRemapper(classMap, methodMap, fieldMap, graph)
        val remapped = LinkedHashMap<String, ClassNode>()
        for (cn in pool.classes.values) {
            val out = ClassNode()
            cn.accept(ClassRemapper(out, remapper))
            remapped[out.name] = out
        }
        pool.classes.clear()
        pool.classes.putAll(remapped)
    }

    private fun stripKotlinMetadata(pool: ClassPool) {
        for (cn in pool.classes.values) {
            cn.visibleAnnotations?.removeAll { it.desc == "Lkotlin/Metadata;" }
        }
    }

    private fun collectKeptClasses(pool: ClassPool): Set<String> {
        val kept = HashSet<String>()
        kept.add("module-info")
        pool.resources["META-INF/MANIFEST.MF"]?.let { bytes ->
            val text = bytes.toString(Charsets.UTF_8)
            Regex("Main-Class:\\s*([\\w.\$]+)").find(text)?.let {
                kept.add(it.groupValues[1].trim().replace('.', '/'))
            }
        }
        for ((path, bytes) in pool.resources) {
            if (!path.startsWith("META-INF/services/")) continue
            bytes.toString(Charsets.UTF_8).lineSequence().forEach { line ->
                val n = line.substringBefore('#').trim()
                if (n.isNotEmpty()) kept.add(n.replace('.', '/'))
            }
        }
        return kept
    }

    private fun computeClassMap(pool: ClassPool, kept: Set<String>) {
        for (cn in pool.classes.values) {
            if (cn.name in kept) continue
            if (cn.access and Opcodes.ACC_MODULE != 0) continue
            val pkg = cn.name.substringBeforeLast('/', "")
            val simple = classNames.next()
            classMap[cn.name] = if (pkg.isEmpty()) simple else "$pkg/$simple"
        }
    }

    private fun computeFieldMap(pool: ClassPool, graph: InheritanceGraph) {
        for (cn in pool.classes.values) {
            val serializable = "java/io/Serializable" in graph.allSupers(cn.name)
            for (f in cn.fields) {
                if (keepField(f, serializable)) continue
                fieldMap["${cn.name}.${f.name}|${f.desc}"] = fieldNames.next()
            }
        }
    }

    private fun keepField(f: FieldNode, serializable: Boolean): Boolean {
        if (f.access and Opcodes.ACC_ENUM != 0) return true
        if (f.name == "\$VALUES") return true
        if (f.name == "serialVersionUID") return true
        return serializable
    }

    private fun computeMethodMap(pool: ClassPool, graph: InheritanceGraph, resolver: LibraryResolver) {
        val virtual = HashMap<String, Set<String>>()
        for (cn in pool.classes.values) {
            virtual[cn.name] = cn.methods.filter { isVirtual(it) }.mapTo(HashSet()) { it.name + it.desc }
        }

        val uf = UnionFind()
        for (cn in pool.classes.values) {
            for (m in cn.methods) {
                if (!isVirtual(m)) continue
                uf.add("${cn.name}.${m.name}${m.desc}")
            }
        }
        for (cn in pool.classes.values) {
            for (m in cn.methods) {
                if (!isVirtual(m)) continue
                val self = "${cn.name}.${m.name}${m.desc}"
                for (sup in graph.allSupers(cn.name)) {
                    if (virtual[sup]?.contains(m.name + m.desc) == true) {
                        uf.union(self, "$sup.${m.name}${m.desc}")
                    }
                }
            }
        }

        val groups = HashMap<String, MutableList<Pair<String, MethodNode>>>()
        for (cn in pool.classes.values) {
            for (m in cn.methods) {
                if (!isVirtual(m)) continue
                val root = uf.find("${cn.name}.${m.name}${m.desc}")
                groups.getOrPut(root) { ArrayList() }.add(cn.name to m)
            }
        }

        for ((_, members) in groups) {
            val sample = members[0].second
            if (keepVirtualGroup(members, sample.name, sample.desc, graph, resolver)) continue
            val newName = methodNames.next()
            for ((owner, m) in members) {
                methodMap["$owner.${m.name}${m.desc}"] = newName
            }
        }

        for (cn in pool.classes.values) {
            for (m in cn.methods) {
                if (isVirtual(m)) continue
                if (!isRenameableNonVirtual(m)) continue
                val key = "${cn.name}.${m.name}${m.desc}"
                if (key in methodMap) continue
                methodMap[key] = methodNames.next()
            }
        }
    }

    private fun isVirtual(m: MethodNode): Boolean {
        if (m.name == "<init>" || m.name == "<clinit>") return false
        if (m.access and Opcodes.ACC_STATIC != 0) return false
        if (m.access and Opcodes.ACC_PRIVATE != 0) return false
        return true
    }

    private fun isRenameableNonVirtual(m: MethodNode): Boolean {
        if (m.name == "<init>" || m.name == "<clinit>") return false
        if (m.access and Opcodes.ACC_NATIVE != 0) return false
        if (isMain(m)) return false
        if (m.name == "values" || m.name == "valueOf") return false
        return true
    }

    private fun isMain(m: MethodNode): Boolean =
        m.name == "main" &&
                m.desc == "([Ljava/lang/String;)V" &&
                m.access and Opcodes.ACC_STATIC != 0 &&
                m.access and Opcodes.ACC_PUBLIC != 0

    private fun keepVirtualGroup(
        members: List<Pair<String, MethodNode>>,
        mName: String,
        mDesc: String,
        graph: InheritanceGraph,
        resolver: LibraryResolver
    ): Boolean {
        for ((_, m) in members) {
            if (m.access and Opcodes.ACC_NATIVE != 0) return true
        }
        if (resolver.isObjectMethod(mName, mDesc)) return true
        for ((owner, _) in members) {
            for (sup in graph.allSupers(owner)) {
                if (graph.inPool(sup)) continue
                val sigs = resolver.signatures(sup) ?: return true
                if (mName + mDesc in sigs) return true
            }
        }
        return false
    }
}

private class UnionFind {
    private val parent = HashMap<String, String>()

    fun add(x: String) {
        parent.putIfAbsent(x, x)
    }

    fun find(x: String): String {
        var r = x
        while (parent[r] != r) r = parent.getValue(r)
        var c = x
        while (parent[c] != r) {
            val next = parent.getValue(c)
            parent[c] = r
            c = next
        }
        return r
    }

    fun union(a: String, b: String) {
        add(a); add(b)
        val ra = find(a)
        val rb = find(b)
        if (ra != rb) parent[ra] = rb
    }
}

private class HierarchyRemapper(
    private val classMap: Map<String, String>,
    private val methodMap: Map<String, String>,
    private val fieldMap: Map<String, String>,
    private val graph: InheritanceGraph
) : Remapper() {

    override fun map(internalName: String): String = classMap[internalName] ?: internalName

    override fun mapMethodName(owner: String, name: String, descriptor: String): String {
        if (name == "<init>" || name == "<clinit>") return name
        val real = resolveMethodOwner(owner, name, descriptor)
        return methodMap["$real.$name$descriptor"] ?: name
    }

    override fun mapFieldName(owner: String, name: String, descriptor: String): String {
        val real = resolveFieldOwner(owner, name, descriptor)
        return fieldMap["$real.$name|$descriptor"] ?: name
    }

    override fun mapInvokeDynamicMethodName(name: String, descriptor: String): String = name

    private fun resolveMethodOwner(owner: String, name: String, desc: String): String {
        if (graph.declaresMethod(owner, name, desc)) return owner
        for (s in graph.allSupers(owner)) if (graph.declaresMethod(s, name, desc)) return s
        return owner
    }

    private fun resolveFieldOwner(owner: String, name: String, desc: String): String {
        if (graph.declaresField(owner, name, desc)) return owner
        for (s in graph.allSupers(owner)) if (graph.declaresField(s, name, desc)) return s
        return owner
    }
}