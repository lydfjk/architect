package ai.architect.tools

import ai.architect.core.ArchitectTool
import ai.architect.core.DeepSeekClient
import com.intellij.openapi.project.Project
import java.io.File

class RunGradleTool(project: Project) : ArchitectTool, BaseCmdTool(project) {
    override fun name() = "run_gradle"
    override fun description() = "Запускает gradle задачу (task)."

    override fun schema() = DeepSeekClient.ToolDef(
        function = DeepSeekClient.FunctionDef(
            name = name(),
            description = description(),
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf("task" to mapOf("type" to "string", "default" to "build")),
                "required" to emptyList<String>()
            )
        )
    )

    override fun invoke(jsonArgs: String): ToolResponse {
        val task = "\"task\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonArgs)?.groupValues?.get(1) ?: "build"
        val base = project.basePath ?: return ToolResponse.error("Unknown project basePath")
        val root = File(base)
        val gradlew = if (System.getProperty("os.name").lowercase().contains("win")) "gradlew.bat" else "gradlew"
        val script = File(root, gradlew).takeIf { it.exists() }?.absolutePath ?: "gradle"
        val out = runCommand(listOf(script, task), root)
        val ok = out.exitCode == 0
        val log = (out.stdout + "\n" + out.stderr).trim()
        return ToolResponse.ok("""{"ok":$ok,"exitCode":${out.exitCode}}""", log.take(10000))
    }
}

