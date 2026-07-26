package org.libreaac.app

internal object FileNames {
    fun safe(value: String): String {
        val cleaned = value
            .map { character ->
                if (character.code < 32 || character in "<>:\"/\\|?*") '-' else character
            }
            .joinToString("")
            .trim()
            .trimEnd('.')
        return cleaned.ifBlank { "vocabulary.obf" }
    }
}

