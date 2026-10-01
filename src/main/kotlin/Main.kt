package dev.kobf

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.file
import dev.kobf.core.Obfuscator
import dev.kobf.transforms.NoOpTransform
import dev.kobf.transforms.StringEncryptTransform

class Kobf : CliktCommand() {

    init {
        versionOption("0.1.0")
    }

    private val input by argument(help = "Input jar")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true)

    private val output by argument(help = "Output jar")
        .file(canBeDir = false)

    private val verbose by option("-v", "--verbose", help = "Print details").flag()

    override fun run() {
        val obfuscator = Obfuscator(listOf(NoOpTransform(), StringEncryptTransform()))
        val result = obfuscator.run(input, output)

        echo("${input.name} -> ${output.name}")
        echo("Classes:    ${result.classCount}")
        echo("Resources: ${result.resourceCount}")
        if (verbose) {
            echo("Transforms: ${result.appliedTransforms.joinToString(", ")}")
        }
    }
}

fun main(args: Array<String>) = Kobf().main(args)
