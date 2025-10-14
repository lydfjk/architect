package ai.architect.tools

import ai.architect.core.ArchitectTool
import ai.architect.core.DeepSeekClient
import com.intellij.openapi.project.Project
import java.io.File

class GitBranchTool(private val project: Project) : ArchitectTool, BaseCmdTool(project) {
    override fun name() = "git_branch"
    override fun description() = "Создаёт/переключает ветку: name (string)."

    override fun schema() = DeepSeekClient.ToolDef(
        function = DeepSeekClient.FunctionDef(
            name = name(),
            description = description(),
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf("name" to mapOf("type" to "string")),
                "required" to listOf("name")
            )
        )
    )

    override fun invoke(jsonArgs: String): ToolResponse {
        val name = "\"name\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonArgs)?.groupValues?.get(1) ?: return ToolResponse.error("name required")
        val root = File(project.basePath!!)
        val out = runCommand(listOf("bash","-lc","git checkout -B \"$name\""), root)
        return ToolResponse.ok("""{"ok":${out.exitCode==0}}""", out.stdout + out.stderr)
    }
}
