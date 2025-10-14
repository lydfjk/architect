package ai.architect.tools

import ai.architect.core.ArchitectTool
import ai.architect.core.DeepSeekClient
import com.intellij.openapi.project.Project
import java.io.File

class RunTestsTool(project: Project) : ArchitectTool, BaseCmdTool(project) {
    override fun name() = "run_tests"
    override fun description() = "Запускает тесты (Gradle или Maven). Аргументы: task (например 'test' или ':app:test')."
    override fun schema() = DeepSeekClient.ToolDef(function =
        DeepSeekClient.FunctionDef(name(), description(), mapOf(
            "type" to "object",
            "properties" to mapOf("task" to mapOf("type" to "string","default" to "test")),
            "required" to listOf<String>()
        ))
    )

    override fun invoke(jsonArgs: String): ToolResponse {
        val task = "\"task\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonArgs)?.groupValues?.get(1) ?: "test"
        val root = File(project.basePath!!)
        val gradlew = if (System.getProperty("os.name").lowercase().contains("win")) "gradlew.bat" else "gradlew"
        val buildFile = File(root, gradlew)
        val out = if (buildFile.exists())
            runCommand(listOf(buildFile.absolutePath, task), root)
        else
            runCommand(listOf("bash","-lc","mvn -q -e -DskipTests=false test"), root)

        val ok = out.exitCode == 0
        val note = out.stdout.ifBlank { out.stderr }
        return ToolResponse.ok("""{"ok":$ok,"exitCode":${out.exitCode}}""", note)
    }
}