/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.tools.packbuild

import java.io.File

/** The `packbuild` command line (plan Task 1). Every command is deterministic except the ECDSA signature. */
object Main {
    /** `issued_at` of the committed fixture manifest: 2026-11-14T00:00:00Z in epoch milliseconds. */
    const val FIXTURE_ISSUED_AT: Long = 1_794_614_400_000

    val USAGE = """
        usage:
          packbuild dict <in.jsonl> <out.db> --lang <lang> --version <N>
          packbuild phrasebook <in.tsv> <out.db> [--version <N>]
          packbuild stickers <packs.tsv> <outDir> [--only <pack id>]
          packbuild manifest <rootDir> <out.json> --packs <packs.tsv> --issued-at <epoch ms>
          packbuild sign <manifest.json> <key.pem>    (writes <manifest.json>.sig, prints the public key)
    """.trimIndent()

    fun run(args: List<String>) {
        val command = args.firstOrNull()
        val (positional, flags) = parse(args.drop(1))
        require(command != null && positional.size == 2) { USAGE }
        val first = File(positional[0])
        val second = File(positional[1])
        fun flag(name: String): String = requireNotNull(flags[name]) { "missing --$name\n$USAGE" }
        fun number(name: String): Long = requireNotNull(flag(name).toLongOrNull()) { "--$name is not a number" }
        val written: File = when (command) {
            "dict" -> second.also { DictPackBuilder.build(first, it, flag("lang"), number("version").toInt()) }
            "phrasebook" -> second.also {
                PhrasebookBuilder.build(first, it, version = if ("version" in flags) number("version").toInt() else 1)
            }
            "stickers" -> second.also { StickerArt.buildPacks(first, it, only = flags["only"]) }
            "manifest" -> second.also {
                val bytes = ManifestWriter.write(first, File(flag("packs")), number("issued-at"))
                it.absoluteFile.parentFile.mkdirs()
                it.writeBytes(bytes)
            }
            "sign" -> File(first.path + ".sig").also {
                val pem = second.readText()
                it.writeText(ManifestSigner.sign(first.readBytes(), pem))
                println("public key (aikb.content.publicKey): ${ManifestSigner.publicKeyBase64(pem)}")
            }
            else -> throw IllegalArgumentException(USAGE)
        }
        println("$command: $written")
    }

    private fun parse(args: List<String>): Pair<List<String>, Map<String, String>> {
        val positional = ArrayList<String>()
        val flags = HashMap<String, String>()
        var i = 0
        while (i < args.size) {
            val a = args[i]
            if (a.startsWith("--")) {
                flags[a.removePrefix("--")] = requireNotNull(args.getOrNull(i + 1)) { "$a needs a value" }
                i += 2
            } else {
                positional += a
                i++
            }
        }
        return positional to flags
    }
}

fun main(args: Array<String>) {
    Main.run(args.toList())
}
