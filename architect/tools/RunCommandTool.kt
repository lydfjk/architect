package ai.architect.tools

import ai.architect.core.ArchitectTool
import ai.architect.core.DeepSeekClient
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.project.Project
import java.io.File

class RunCommandTool(private val project: Project) : ArchitectTool {
    override fun name() = "run_command"
    override fun description() = "Запускает команду в корне проекта. Аргументы: cmd (строка)."

    override fun schema() = DeepSeekClient.ToolDef(
        function = DeepSeekClient.FunctionDef(
            name = name(),
            description = description(),
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf("cmd" to mapOf("type" to "string")),
                "required" to listOf("cmd")
            )
        )
    )

    override fun invoke(jsonArgs: String): ToolResponse {
        val cmd = "\"cmd\"\\s*:\\s*\"([\\s\\S]+)\"".toRegex().find(jsonArgs)?.groupValues?.get(1)?.unescapeJson()
            ?: return ToolResponse.error("cmd required")

        val base = project.basePath ?: return ToolResponse.error("Unknown project basePath")
        val workDir = File(base)
        val line = if (System.getProperty("os.name").lowercase().contains("win"))
            listOf("cmd.exe", "/c", cmd) else listOf("bash","-lc", cmd)

        val handler = CapturingProcessHandler(GeneralCommandLine(line).withWorkDirectory(workDir))
        val out = handler.runProcess(120_000)
        val ok = out.exitCode == 0
        val body = (out.stdout + "\n" + out.stderr).trim()
        return ToolResponse.ok("""{"ok":$ok,"exitCode":${out.exitCode}}""", body.take(8000))
    }
}

private fun String.unescapeJson(): String = this.replace("\\\"","\"").replace("\\\\","\\")
