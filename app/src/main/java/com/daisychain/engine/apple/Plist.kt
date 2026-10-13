package com.daisychain.engine.apple

import java.io.ByteArrayInputStream
import java.io.StringReader
import java.util.Base64
import java.util.Date
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** The file is not a property list we can read. */
class PlistException(message: String) : Exception(message)

/**
 * Apple property lists, the format of `.shortcut` files. Reads the binary (`bplist00`) and XML forms and writes XML.
 * Values are plain Kotlin: Map<String, Any?>, List<Any?>, String, Long, Double, Boolean, ByteArray and java.util.Date.
 */
object Plist {
    fun isBinary(bytes: ByteArray) = bytes.size > 8 && String(bytes, 0, 6, Charsets.US_ASCII) == "bplist"

    fun parse(bytes: ByteArray): Any? = if (isBinary(bytes)) BinaryReader(bytes).root() else parseXml(bytes)

    // ------------------------------------------------------------------ XML

    private fun parseXml(bytes: ByteArray): Any? {
        val factory = DocumentBuilderFactory.newInstance().apply {
            // The file comes from the internet: never fetch the DTD or any external entity.
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            isValidating = false
        }
        val document = try {
            factory.newDocumentBuilder().apply { setEntityResolver { _, _ -> InputSource(StringReader("")) } }.parse(ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            throw PlistException("Not a property list")
        }
        val root = document.documentElement
        if (root.tagName != "plist") throw PlistException("Not a property list")
        val first = generateSequence(root.firstChild) { it.nextSibling }.filterIsInstance<Element>().firstOrNull()
            ?: throw PlistException("Empty property list")
        return xmlValue(first, 0)
    }

    private fun xmlValue(e: Element, depth: Int): Any? {
        if (depth > 64) throw PlistException("Too deeply nested")
        val children = generateSequence(e.firstChild) { it.nextSibling }.filterIsInstance<Element>().toList()
        return when (e.tagName) {
            "dict" -> {
                val map = LinkedHashMap<String, Any?>()
                var i = 0
                while (i + 1 < children.size) {
                    map[children[i].textContent] = xmlValue(children[i + 1], depth + 1)
                    i += 2
                }
                map
            }
            "array" -> children.map { xmlValue(it, depth + 1) }
            "string" -> e.textContent
            "integer" -> e.textContent.trim().toLongOrNull() ?: e.textContent.trim().toBigInteger().toLong()
            "real" -> e.textContent.trim().toDouble()
            "true" -> true
            "false" -> false
            "data" -> Base64.getMimeDecoder().decode(e.textContent.trim())
            "date" -> runCatching { Date(java.time.Instant.parse(e.textContent.trim()).toEpochMilli()) }.getOrDefault(Date(0))
            else -> throw PlistException("Unknown element ${e.tagName}")
        }
    }

    // ------------------------------------------------------------------ binary

    private class BinaryReader(private val b: ByteArray) {
        private val offsetSize: Int
        private val refSize: Int
        private val objectCount: Long
        private val top: Long
        private val tableStart: Long

        init {
            if (b.size < 40) throw PlistException("Too short")
            val t = b.size - 32
            offsetSize = b[t + 6].toInt() and 0xFF
            refSize = b[t + 7].toInt() and 0xFF
            objectCount = unsigned(t + 8, 8)
            top = unsigned(t + 16, 8)
            tableStart = unsigned(t + 24, 8)
            if (offsetSize !in 1..8 || refSize !in 1..8 || objectCount <= 0 || top >= objectCount ||
                tableStart + objectCount * offsetSize > b.size
            ) throw PlistException("Corrupt property list")
        }

        private fun unsigned(at: Int, size: Int): Long {
            if (at < 0 || at + size > b.size) throw PlistException("Corrupt property list")
            var v = 0L
            for (i in 0 until size) v = (v shl 8) or (b[at + i].toLong() and 0xFF)
            return v
        }

        fun root(): Any? = obj(top, 0)

        private fun offsetOf(index: Long): Int {
            if (index < 0 || index >= objectCount) throw PlistException("Corrupt property list")
            return unsigned((tableStart + index * offsetSize).toInt(), offsetSize).toInt()
        }

        /** Size in a marker: the low nibble, or an integer object when that nibble is 0xF. Returns (size, bytes used after the marker). */
        private fun sizeAt(pos: Int, info: Int): Pair<Int, Int> {
            if (info != 0xF) return info to 0
            val intMarker = b[pos + 1].toInt() and 0xFF
            if (intMarker shr 4 != 1) throw PlistException("Corrupt property list")
            val bytes = 1 shl (intMarker and 0xF)
            val v = unsigned(pos + 2, bytes)
            if (v < 0 || v > b.size) throw PlistException("Corrupt property list")
            return v.toInt() to (1 + bytes)
        }

        private fun obj(index: Long, depth: Int): Any? {
            if (depth > 64) throw PlistException("Too deeply nested")
            val pos = offsetOf(index)
            val marker = b[pos].toInt() and 0xFF
            val type = marker shr 4
            val info = marker and 0xF
            return when (type) {
                0x0 -> when (info) {
                    0x8 -> false
                    0x9 -> true
                    else -> null
                }
                0x1 -> {
                    val size = 1 shl info
                    if (size > 8) throw PlistException("Unsupported integer")
                    val v = unsigned(pos + 1, size)
                    if (size == 8) v else v
                }
                0x2 -> when (1 shl info) {
                    4 -> java.lang.Float.intBitsToFloat(unsigned(pos + 1, 4).toInt()).toDouble()
                    8 -> java.lang.Double.longBitsToDouble(unsigned(pos + 1, 8))
                    else -> throw PlistException("Unsupported real")
                }
                0x3 -> Date(978307200000L + (java.lang.Double.longBitsToDouble(unsigned(pos + 1, 8)) * 1000).toLong())
                0x4 -> {
                    val (n, used) = sizeAt(pos, info)
                    val start = pos + 1 + used
                    if (start + n > b.size) throw PlistException("Corrupt property list")
                    b.copyOfRange(start, start + n)
                }
                0x5 -> {
                    val (n, used) = sizeAt(pos, info)
                    val start = pos + 1 + used
                    if (start + n > b.size) throw PlistException("Corrupt property list")
                    String(b, start, n, Charsets.ISO_8859_1)
                }
                0x6 -> {
                    val (n, used) = sizeAt(pos, info)
                    val start = pos + 1 + used
                    if (start + n * 2 > b.size) throw PlistException("Corrupt property list")
                    String(b, start, n * 2, Charsets.UTF_16BE)
                }
                0x8 -> unsigned(pos + 1, info + 1)
                0xA -> {
                    val (n, used) = sizeAt(pos, info)
                    val start = pos + 1 + used
                    if (start + n.toLong() * refSize > b.size) throw PlistException("Corrupt property list")
                    (0 until n).map { obj(unsigned(start + it * refSize, refSize), depth + 1) }
                }
                0xD -> {
                    val (n, used) = sizeAt(pos, info)
                    val start = pos + 1 + used
                    if (start + 2L * n * refSize > b.size) throw PlistException("Corrupt property list")
                    val map = LinkedHashMap<String, Any?>()
                    for (i in 0 until n) {
                        val key = obj(unsigned(start + i * refSize, refSize), depth + 1) as? String ?: throw PlistException("Corrupt property list")
                        map[key] = obj(unsigned(start + (n + i) * refSize, refSize), depth + 1)
                    }
                    map
                }
                else -> throw PlistException("Unknown object type")
            }
        }
    }

    // ------------------------------------------------------------------ writing (XML)

    fun toXml(root: Any?): ByteArray {
        val out = StringBuilder()
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        out.append("<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n")
        out.append("<plist version=\"1.0\">\n")
        write(out, root, 0)
        out.append("</plist>\n")
        return out.toString().toByteArray(Charsets.UTF_8)
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun write(out: StringBuilder, v: Any?, depth: Int) {
        val pad = "\t".repeat(depth)
        when (v) {
            is Map<*, *> -> {
                if (v.isEmpty()) { out.append(pad).append("<dict/>\n"); return }
                out.append(pad).append("<dict>\n")
                for ((k, value) in v) {
                    out.append(pad).append("\t<key>").append(escape(k.toString())).append("</key>\n")
                    write(out, value, depth + 1)
                }
                out.append(pad).append("</dict>\n")
            }
            is List<*> -> {
                if (v.isEmpty()) { out.append(pad).append("<array/>\n"); return }
                out.append(pad).append("<array>\n")
                v.forEach { write(out, it, depth + 1) }
                out.append(pad).append("</array>\n")
            }
            is String -> out.append(pad).append("<string>").append(escape(v)).append("</string>\n")
            is Boolean -> out.append(pad).append(if (v) "<true/>\n" else "<false/>\n")
            is Int -> out.append(pad).append("<integer>").append(v).append("</integer>\n")
            is Long -> out.append(pad).append("<integer>").append(v).append("</integer>\n")
            is Double -> out.append(pad).append("<real>").append(v).append("</real>\n")
            is ByteArray -> out.append(pad).append("<data>").append(Base64.getEncoder().encodeToString(v)).append("</data>\n")
            is Date -> out.append(pad).append("<date>").append(java.time.Instant.ofEpochMilli(v.time)).append("</date>\n")
            null -> out.append(pad).append("<string></string>\n")
            else -> out.append(pad).append("<string>").append(escape(v.toString())).append("</string>\n")
        }
    }
}
