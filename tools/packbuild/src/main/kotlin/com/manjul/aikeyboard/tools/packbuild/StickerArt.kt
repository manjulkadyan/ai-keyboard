/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.tools.packbuild

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.GeneralPath
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Random
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generated sticker art (plan decision 1): [COUNT] PNGs of [SIZE] x [SIZE] per pack, drawn with `java.awt` in headless
 * mode from shapes and colours only. Sticker `i` uses `Random(seed * 31 + i)`, so the same seed and index always give
 * the same bytes. Original works of the AI Keyboard contributors, no source art.
 */
object StickerArt {
    const val SIZE = 512
    const val COUNT = 8
    const val MAX_BYTES = 60 * 1024

    /** One row of `content/stickers/packs.tsv`. */
    data class PackRow(val id: String, val title: String, val premium: Boolean, val version: Int, val seed: Long)

    val HEADER = listOf("pack_id", "title", "premium", "version", "seed")

    fun readPacks(tsv: File): List<PackRow> {
        val lines = tsv.readLines().filter { it.isNotBlank() }
        require(lines.firstOrNull()?.split('\t') == HEADER) { "the first line must be ${HEADER.joinToString("\\t")}" }
        val rows = lines.drop(1).mapIndexed { i, raw ->
            val f = raw.split('\t')
            require(f.size == HEADER.size && f.all { it.isNotBlank() }) { "line ${i + 2} needs ${HEADER.size} fields" }
            require(f[0].matches(Regex("[a-z0-9_]+"))) { "line ${i + 2}: pack_id must be [a-z0-9_]+" }
            PackRow(
                id = f[0],
                title = f[1],
                premium = f[2].toBooleanStrictOrNull() ?: error("line ${i + 2}: premium must be true or false"),
                version = f[3].toIntOrNull()?.takeIf { it > 0 } ?: error("line ${i + 2}: version must be positive"),
                seed = f[4].toLongOrNull() ?: error("line ${i + 2}: seed is not a number"),
            )
        }
        require(rows.map { it.id }.distinct().size == rows.size) { "duplicate pack_id in $tsv" }
        return rows
    }

    /** Writes `<outDir>/<pack id>/01.png` to `08.png` for every pack in [packsTsv], or only for the pack [only]. */
    fun buildPacks(packsTsv: File, outDir: File, only: String? = null) {
        val packs = readPacks(packsTsv).filter { only == null || it.id == only }
        require(packs.isNotEmpty()) { "no pack ${only.orEmpty()} in $packsTsv" }
        for (pack in packs) {
            val dir = File(outDir, pack.id)
            dir.mkdirs()
            dir.listFiles { f -> f.name.endsWith(".png") }?.forEach { it.delete() }
            for (i in 1..COUNT) File(dir, "%02d.png".format(i)).writeBytes(png(pack.seed, i))
        }
    }

    fun png(seed: Long, index: Int): ByteArray {
        val img = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            draw(g, Random(seed * 31 + index), index)
        } finally {
            g.dispose()
        }
        val bytes = ByteArrayOutputStream().also { check(ImageIO.write(img, "png", it)) }.toByteArray()
        check(bytes.size <= MAX_BYTES) { "sticker $index of seed $seed is ${bytes.size} bytes" }
        return bytes
    }

    private fun draw(g: Graphics2D, rnd: Random, index: Int) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        val hue = rnd.nextFloat()
        val base = Color.getHSBColor(hue, 0.55f + rnd.nextFloat() * 0.3f, 0.85f)
        val accent = Color.getHSBColor((hue + 0.45f + rnd.nextFloat() * 0.1f) % 1f, 0.7f, 0.95f)
        val ink = Color(0x22, 0x22, 0x2A)
        val pad = 24.0
        g.color = base
        g.fill(RoundRectangle2D.Double(pad, pad, SIZE - 2 * pad, SIZE - 2 * pad, 120.0, 120.0))
        g.color = Color.WHITE
        g.stroke = BasicStroke(14f)
        g.draw(RoundRectangle2D.Double(pad, pad, SIZE - 2 * pad, SIZE - 2 * pad, 120.0, 120.0))
        g.color = accent
        val c = SIZE / 2.0
        val r = 120.0 + rnd.nextInt(40)
        when ((index - 1) % COUNT) {
            0 -> g.fill(Ellipse2D.Double(c - r, c - r, 2 * r, 2 * r))
            1 -> g.fill(star(c, c, r, r * 0.45, 5, rnd.nextDouble() * PI))
            2 -> g.fill(heart(c, c, r))
            3 -> g.fill(polygon(c, c, r, 3, -PI / 2))
            4 -> g.fill(polygon(c, c, r, 6, rnd.nextDouble() * PI))
            5 -> g.fill(star(c, c, r, r * 0.7, 8, 0.0))
            6 -> g.fill(RoundRectangle2D.Double(c - r, c - r * 0.75, 2 * r, 1.5 * r, 80.0, 80.0))
            else -> g.fill(polygon(c, c, r, 4, PI / 4))
        }
        // A simple face so the sticker reads at chat size.
        g.color = ink
        val eye = 18.0 + rnd.nextInt(8)
        val dx = 40.0 + rnd.nextInt(12)
        g.fill(Ellipse2D.Double(c - dx - eye, c - 30 - eye, 2 * eye, 2 * eye))
        g.fill(Ellipse2D.Double(c + dx - eye, c - 30 - eye, 2 * eye, 2 * eye))
        g.stroke = BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val smile = 30.0 + rnd.nextInt(30)
        g.draw(Arc2D.Double(c - 50, c - 10, 100.0, smile, 200.0, 140.0, Arc2D.OPEN))
    }

    private fun polygon(cx: Double, cy: Double, r: Double, n: Int, rot: Double): Path2D = GeneralPath().apply {
        for (i in 0 until n) {
            val a = rot + 2 * PI * i / n
            if (i == 0) moveTo(cx + r * cos(a), cy + r * sin(a)) else lineTo(cx + r * cos(a), cy + r * sin(a))
        }
        closePath()
    }

    private fun star(cx: Double, cy: Double, outer: Double, inner: Double, points: Int, rot: Double): Path2D =
        GeneralPath().apply {
            for (i in 0 until points * 2) {
                val r = if (i % 2 == 0) outer else inner
                val a = rot - PI / 2 + PI * i / points
                if (i == 0) moveTo(cx + r * cos(a), cy + r * sin(a)) else lineTo(cx + r * cos(a), cy + r * sin(a))
            }
            closePath()
        }

    private fun heart(cx: Double, cy: Double, r: Double): Path2D = GeneralPath().apply {
        moveTo(cx, cy + r)
        curveTo(cx - r * 1.6, cy - r * 0.1, cx - r * 0.7, cy - r * 1.2, cx, cy - r * 0.45)
        curveTo(cx + r * 0.7, cy - r * 1.2, cx + r * 1.6, cy - r * 0.1, cx, cy + r)
        closePath()
    }
}
