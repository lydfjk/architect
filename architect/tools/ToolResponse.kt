package ai.architect.tools

data class ToolResponse(val json: String, val humanReadable: String) {
    companion object {
        fun ok(json: String, note: String = "") = ToolResponse(json, note)
        fun error(msg: String) = ToolResponse("""{"ok":false,"error":"$msg"}""", msg)
    }
}