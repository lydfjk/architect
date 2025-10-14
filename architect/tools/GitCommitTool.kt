package ai.architect.tools

import ai.architect.core.ArchitectTool
import ai.architect.core.DeepSeekClient
import com.intellij.openapi.project.Project
import java.io.File

class GitCommitTool(private val project: Project) : ArchitectTool, BaseCmdTool(project) {
    override fun name() = "git_commit"
    override fun description() = "git add -A && git commit -m message"

    override fun schema() = DeepSeekClient.ToolDef(
        function = DeepSeekClient.FunctionDef(
            name = name(),
            description = description(),
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf("message" to mapOf("type" to "string")),
                "required" to listOf("message")
            )
        )
    )

    override fun invoke(jsonArgs: String): ToolResponse {
        val msg = "\"message\"\\s*:\\s*\"([\\s\\S]*?)\"".toRegex().find(jsonArgs)?.groupValues?.get(1)?.unescapeJson() ?: "update"
        val root = File(project.basePath!!)
        val add = runCommand(listOf("bash","-lc","git add -A"), root)
        val commit = runCommand(listOf("bash","-lc","git commit -m \"$msg\" || true"), root)
        return ToolResponse.ok("""{"ok":true}""", (add.stdout + "\n" + commit.stdout).trim())
    }
}

private fun String.unescapeJson(): String = this.replace("\\\"","\"").replace("\\\\","\\")
