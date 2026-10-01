package dev.kobf

import dev.kobf.core.Obfuscator
import dev.kobf.transforms.NoOpTransform
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoundTripTest {

    @Test
    fun noopPreservesClassesAndResources() {
        val tmp = File.createTempFile("kobf-test", "").apply { delete(); mkdirs() }
        val input = File(tmp, "in.jar")
        val output = File(tmp, "out.jar")

        val resourceBytes = "hello=world".toByteArray()
        buildJar(input, resourceBytes)

        val result = Obfuscator(listOf(NoOpTransform())).run(input, output)

        assertEquals(1, result.classCount)
        assertTrue(output.exists())

        JarFile(output).use { jar ->
            val classEntry = jar.getJarEntry("dev/kobf/sample/Hello.class")
            assertTrue(classEntry != null, "Klasse fehlt im Output")
            val classBytes = jar.getInputStream(classEntry).readBytes()
            assertEquals("dev/kobf/sample/Hello", ClassReader(classBytes).className)

            val res = jar.getJarEntry("data/config.properties")
            assertTrue(res != null, "Ressource fehlt im Output")
            assertContentEquals(resourceBytes, jar.getInputStream(res).readBytes())

            assertTrue(jar.getJarEntry("META-INF/MANIFEST.MF") != null, "Manifest fehlt")
        }

        tmp.deleteRecursively()
    }

    private fun buildJar(target: File, resourceBytes: ByteArray) {
        val manifest = "Manifest-Version: 1.0\r\n\r\n".toByteArray()
        JarOutputStream(target.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            out.write(manifest)
            out.closeEntry()

            out.putNextEntry(ZipEntry("dev/kobf/sample/Hello.class"))
            out.write(sampleClass())
            out.closeEntry()

            out.putNextEntry(ZipEntry("data/config.properties"))
            out.write(resourceBytes)
            out.closeEntry()
        }
    }

    private fun sampleClass(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(
            Opcodes.V21,
            Opcodes.ACC_PUBLIC,
            "dev/kobf/sample/Hello",
            null,
            "java/lang/Object",
            null
        )
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor.visitCode()
        ctor.visitVarInsn(Opcodes.ALOAD, 0)
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor.visitInsn(Opcodes.RETURN)
        ctor.visitMaxs(0, 0)
        ctor.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }
}