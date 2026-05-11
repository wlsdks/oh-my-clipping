package com.ohmyclipping.ai

object JsonContentNormalizer {

    fun escapeControlCharsInStrings(rawJson: String): String {
        if (rawJson.isEmpty()) return rawJson

        val out = StringBuilder(rawJson.length + 32)
        var inString = false
        var escaping = false

        rawJson.forEach { ch ->
            if (!inString) {
                out.append(ch)
                if (ch == '"') {
                    inString = true
                }
                return@forEach
            }

            if (escaping) {
                out.append(ch)
                escaping = false
                return@forEach
            }

            when (ch) {
                '\\' -> {
                    out.append(ch)
                    escaping = true
                }

                '"' -> {
                    out.append(ch)
                    inString = false
                }

                '\n' -> out.append("\\n")
                '\r' -> out.append("\\n")
                '\t' -> out.append("\\t")
                else -> out.append(ch)
            }
        }

        return out.toString()
    }
}
