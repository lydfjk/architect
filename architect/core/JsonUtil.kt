package ai.architect.core

fun String.toJsonString(): String = buildString {
    append('"')
    for (ch in this@toJsonString) {
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(ch)
        }
    }
    append('"')
}
