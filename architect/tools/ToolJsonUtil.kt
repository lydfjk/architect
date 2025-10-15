package ai.architect.tools

internal fun String.escapeJson(): String = buildString {
    append('"')
    for (ch in this@escapeJson) {
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

internal fun String.unescapeJson(): String =
    this.replace("\\\"", "\"").replace("\\\\", "\\")

internal fun List<String>.toJsonArray(): String =
    "[" + joinToString(",") { it.replace("\\", "\\\\").let { v -> "\"$v\"" } } + "]"

internal fun String.matchesGlob(glob: String): Boolean {
    val regex = glob
        .replace(".", "\\.")
        .replace("**", ".*")
        .replace("*", "[^/]*")
    return Regex("^$regex$").matches(this)
}