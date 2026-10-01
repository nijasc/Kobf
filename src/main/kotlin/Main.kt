package dev.kobf

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.file
import dev.kobf.core.Obfuscator

class Kobf : CliktCommand() {

    init {
        versionOption("0.1.0")
    }

    override fun help(context: Context) =
        "Kobf — a Kotlin-optimized JVM bytecode obfuscator. " +
                "All transforms run at full strength by default; use --disable or --plain-strings to weaken."

    override fun helpEpilog(context: Context) = buildString {
        append("Transforms:\n")
        append(Pipeline.describe())
        append("\n\nExamples:\n")
        append("  kobf app.jar out.jar\n")
        append("  kobf app.jar out.jar --disable rename\n")
        append("  kobf app.jar out.jar --disable strings,rename\n")
        append("  kobf app.jar out.jar --plain-strings -cp libs/dep.jar")
    }

    private val input by argument(help = "Input jar")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true)

    private val output by argument(help = "Output jar")
        .file(canBeDir = false)

    private val disableRaw by option("--disable", help = "Disable transforms by id (comma-separated, repeatable)")
        .multiple()

    private val enableRaw by option("--enable", help = "Enable off-by-default transforms by id")
        .multiple()

    private val plainStrings by option("--plain-strings", help = "Do not bind the string key to the caller").flag()

    private val classpath by option("-cp", "--classpath", help = "Dependency jar for type resolution (repeatable)")
        .file(mustExist = true, canBeDir = false)
        .multiple()

    private val verbose by option("-v", "--verbose", help = "Print details").flag()

    override fun run() {
        val disabled = splitIds(disableRaw)
        val enabled = splitIds(enableRaw)

        (disabled + enabled).filter { it !in Pipeline.ids }.forEach {
            echo("warning: unknown transform id '$it'", err = true)
        }

        val transforms = Pipeline.resolve(enabled, disabled, PipelineOptions(contextStrings = !plainStrings))
        val result = Obfuscator(transforms).run(input, output, classpath = classpath)

        echo("${input.name} -> ${output.name}")
        echo("Classes:    ${result.classCount}")
        echo("Resources: ${result.resourceCount}")
        if (verbose) {
            echo("Transforms: ${result.appliedTransforms.joinToString(", ")}")
        }
    }

    private fun splitIds(raw: List<String>): Set<String> =
        raw.flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

fun main(args: Array<String>) = Kobf().main(args)