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
          packbuild dict <in.jsonl> <out.db> --lang <lang> --version <N> <licence>
            <licence> is either --fixture (team-written content, Apache-2.0) or all four of
            --source <text> --licence-name <SPDX id> --licence-url <url> --attribution <text>
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
            "dict" -> second.also {
                DictPackBuilder.build(first, it, flag("lang"), number("version").toInt(), dictLicence(flags))
            }
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

    /**
     * The licence that `dict` stamps into `meta` (and so into the manifest). There is no default: a real
     * Wiktextract extract is CC-BY-SA (R-08, FR-23 AC2), so a missing flag fails the build instead of mislabelling it.
     */
    private fun dictLicence(flags: Map<String, String>): PackLicence {
        val given = LICENCE_FLAGS.filter { it in flags }
        if (FIXTURE_FLAG in flags) {
            require(given.isEmpty()) { "--$FIXTURE_FLAG cannot be combined with ${given.joinToString { "--$it" }}" }
            return DictPackBuilder.FIXTURE_LICENCE
        }
        val missing = LICENCE_FLAGS - given.toSet()
        require(missing.isEmpty()) { "dict needs --$FIXTURE_FLAG or ${missing.joinToString { "--$it" }}\n$USAGE" }
        fun value(name: String) = flags.getValue(name).also { require(it.isNotBlank()) { "--$name is empty" } }
        return PackLicence(value("source"), value("licence-name"), value("licence-url"), value("attribution"))
    }

    private const val FIXTURE_FLAG = "fixture"
    private val LICENCE_FLAGS = listOf("source", "licence-name", "licence-url", "attribution")

    /** Flags that take no value. */
    private val SWITCHES = setOf(FIXTURE_FLAG)

    private fun parse(args: List<String>): Pair<List<String>, Map<String, String>> {
        val positional = ArrayList<String>()
        val flags = HashMap<String, String>()
        var i = 0
        while (i < args.size) {
            val a = args[i]
            if (a.startsWith("--") && a.removePrefix("--") in SWITCHES) {
                flags[a.removePrefix("--")] = "true"
                i++
            } else if (a.startsWith("--")) {
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
