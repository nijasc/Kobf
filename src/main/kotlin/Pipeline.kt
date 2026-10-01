package dev.kobf

import dev.kobf.core.Transformation
import dev.kobf.transforms.DebugStripTransform
import dev.kobf.transforms.RenameTransform
import dev.kobf.transforms.StringEncryptTransform

data class PipelineOptions(val contextStrings: Boolean = true)

class TransformInfo(
    val id: String,
    val description: String,
    val enabledByDefault: Boolean,
    val build: (PipelineOptions) -> Transformation
)

object Pipeline {

    val entries: List<TransformInfo> = listOf(
        TransformInfo("strip-debug", "Remove debug info (variable names, line numbers, source file)", true) {
            DebugStripTransform()
        },
        TransformInfo("rename", "Obfuscate class, method and field names", true) {
            RenameTransform()
        },
        TransformInfo("strings", "Encrypt string constants", true) { opts ->
            StringEncryptTransform(contextBound = opts.contextStrings)
        }
    )

    val ids: Set<String> get() = entries.mapTo(LinkedHashSet()) { it.id }

    fun resolve(enabled: Set<String>, disabled: Set<String>, options: PipelineOptions): List<Transformation> =
        entries
            .filter { if (it.enabledByDefault) it.id !in disabled else it.id in enabled }
            .map { it.build(options) }

    fun describe(): String = entries.joinToString("\n") {
        val state = if (it.enabledByDefault) "on" else "off"
        "  " + it.id.padEnd(14) + it.description + "  [default: " + state + "]"
    }
}