package ai.architect.tools

import ai.architect.core.ArchitectTool
import ai.architect.core.DeepSeekClient
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import java.io.File

class FindReplaceTool(private val project: Project) : ArchitectTool {
    override fun name() = "find_replace"
    override fun description() = "Массовая замена в файлах по glob; поддержка regex; dry_run."
    override fun schema() = DeepSeekClient.ToolDef(function =
        DeepSeekClient.FunctionDef(name(), description(), mapOf(
            "type" to "object",
            "properties" to mapOf(
                "dir" to mapOf("type" to "string","default" to "."),
                "glob" to mapOf("type" to "string","default" to "**/*.*"),
                "find" to mapOf("type" to "string"),
                "replace" to mapOf("type" to "string","default" to ""),
                "regex" to mapOf("type" to "boolean","default" to false),
                "dry_run" to mapOf("type" to "boolean","default" to true)
            ),
            "required" to listOf("find")
        ))
    )

    override fun invoke(jsonArgs: String): ToolResponse {
        fun parse(key: String, def: String? = null): String? =
            """"$key"\s*:\s*"(.*?)"""".toRegex(RegexOption.DOT_MATCHES_ALL).find(jsonArgs)?.groupValues?.get(1) ?: def
        fun parseB(key: String, def: Boolean) =
            """"$key"\s*:\s*(true|false)""".toRegex().find(jsonArgs)?.groupValues?.get(1)?.toBoolean() ?: def

        val dir = parse("dir",".")!!
        val glob = parse("glob","**/*.*")!!
        val find = parse("find") ?: return ToolResponse.error("find?")
        val replace = parse("replace","")!!
        val regex = parseB("regex", false)
        val dry = parseB("dry_run", true)

        val base = File(project.basePath ?: return ToolResponse.error("no project"))
        val root = File(base, dir)
        val files = root.walkTopDown().filter { it.isFile && it.relativeTo(base).path.matchesGlob(glob) }.toList()

        var changed = 0
        val changes = mutableListOf<String>()
        files.forEach { file ->
            val src = file.readText()
            val dst = if (regex) src.replace(find.toRegex(RegexOption.MULTILINE), replace) else src.replace(find, replace)
            if (src != dst) {
                changed++
                changes += file.relativeTo(base).path
                if (!dry) {
                    WriteCommandAction.runWriteCommandAction(project) {
                        file.writeText(dst)
                    }
                }
            }
        }
        val pretty = "Файлов изменено: $changed\n" + changes.joinToString("\n") { "• $it" }
        val json = """{"ok":true,"changed":$changed,"dry_run":$dry,"files":${changes.toJsonArray()}}"""
        return ToolResponse.ok(json, pretty)
    }
}

// Утилиты
private fun String.matchesGlob(glob: String): Boolean {
    val regex = glob.replace(".", "\\.").replace("**", ".+").replace("*", "[^/]*")
    return Regex("^$regex$").matches(this)
}
private fun List<String>.toJsonArray(): String =
    "[" + this.joinToString(",") { "\"" + it.replace("\\", "\\\\") + "\"" } + "]"
