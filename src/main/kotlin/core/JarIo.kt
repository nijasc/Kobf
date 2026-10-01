package dev.kobf.core

import org.objectweb.asm.ClassReader
import java.io.File
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

object JarIo {

    fun read(file: File, pool: ClassPool) {
        JarFile(file).use { jar ->
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val bytes = jar.getInputStream(entry).readBytes()
                if (entry.name.endsWith(".class") && isParseable(bytes)) {
                    pool.add(bytes)
                } else {
                    pool.addResource(entry.name, bytes)
                }
            }
        }
    }

    fun write(file: File, pool: ClassPool, serialize: (String) -> ByteArray) {
        file.parentFile?.mkdirs()
        JarOutputStream(file.outputStream().buffered()).use { out ->
            val manifest = "META-INF/MANIFEST.MF"
            pool.resources[manifest]?.let { writeEntry(out, manifest, it) }

            for ((name, _) in pool.classes) {
                writeEntry(out, "$name.class", serialize(name))
            }
            for ((path, bytes) in pool.resources) {
                if (path == manifest) continue
                writeEntry(out, path, bytes)
            }
        }
    }

    private fun writeEntry(out: JarOutputStream, name: String, bytes: ByteArray) {
        out.putNextEntry(ZipEntry(name))
        out.write(bytes)
        out.closeEntry()
    }

    private fun isParseable(bytes: ByteArray): Boolean {
        return bytes.size >= 4 && try {
            ClassReader(bytes).className
            true
        } catch (_: Throwable) {
            false
        }
    }
}