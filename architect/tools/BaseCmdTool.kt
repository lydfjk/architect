package ai.architect.tools

import ai.architect.core.ArchitectTool
import ai.architect.core.DeepSeekClient
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.project.Project
import java.io.File

abstract class BaseCmdTool(protected val project: Project) {
    protected fun runCommand(cmd: List<String>, workDir: File): ProcessOutput {
        val command = GeneralCommandLine(cmd).withWorkDirectory(workDir)
        val handler = CapturingProcessHandler(command)
        return handler.runProcess(120_000)
    }
}

class RunGradleTool(project: Project) : ArchitectTool, BaseCmdTool(project) {
    override fun name() = "run_gradle"
    override fun description() = "Выполняет Gradle-задачу через ./gradlew."
    override fun schema() = DeepSeekClient.ToolDef(function =
        DeepSeekClient.FunctionDef(name(), description(), mapOf(
            "type" to "object",
            "properties" to mapOf("task" to mapOf("type" to "string")),
            "required" to listOf("task")
        ))
    )
    override fun invoke(jsonArgs: String): ToolResponse {
        val task = "\"task\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonArgs)?.groupValues?.get(1) ?: return ToolResponse.error("task?")
        val root = File(project.basePath!!)
        val gradlew = if (System.getProperty("os.name").lowercase().contains("win")) "gradlew.bat" else "gradlew"
        val out = runCommand(listOf(File(root, gradlew).absolutePath, task), root)
        val ok = out.exitCode == 0
        return ToolResponse.ok("""{"ok":$ok,"exitCode":${out.exitCode}}""", out.stdout.ifBlank { out.stderr })
    }
}

class RunCommandTool(project: Project) : ArchitectTool, BaseCmdTool(project) {
    override fun name() = "run_command"
    override fun description() = "Запускает консольную команду в корне проекта (белый список безопасных команд)."
    override fun schema() = DeepSeekClient.ToolDef(function =
        DeepSeekClient.FunctionDef(name(), description(), mapOf(
            "type" to "object",
            "properties" to mapOf("cmd" to mapOf("type" to "string")),
            "required" to listOf("cmd")
        ))
    )
    override fun invoke(jsonArgs: String): ToolResponse {
        val cmd = "\"cmd\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonArgs)?.groupValues?.get(1) ?: return ToolResponse.error("cmd?")
        val allow = listOf("gradlew", "git ", "./", "bash ", "python ", "adb ")
        if (allow.none { cmd.startsWith(it) }) return ToolResponse.error("Команда запрещена политикой")
        val out = runCommand(listOf("bash", "-lc", cmd), File(project.basePath!!))
        val ok = out.exitCode == 0
        return ToolResponse.ok("""{"ok":$ok,"exitCode":${out.exitCode}}""", out.stdout.ifBlank { out.stderr })
    }
}

class GitBranchTool(project: Project) : ArchitectTool, BaseCmdTool(project) {
    override fun name() = "git_branch"
    override fun description() = "Создает/переключает feature-ветку; может создать изолированный worktree."
    override fun schema() = DeepSeekClient.ToolDef(function =
        DeepSeekClient.FunctionDef(name(), description(), mapOf(
            "type" to "object",
            "properties" to mapOf(
                "branch" to mapOf("type" to "string"),
                "worktree" to mapOf("type" to "boolean")
            ),
            "required" to listOf("branch")
        ))
    )
    override fun invoke(jsonArgs: String): ToolResponse {
        val branch = "\"branch\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonArgs)?.groupValues?.get(1) ?: return ToolResponse.error("branch?")
        val worktree = "\"worktree\"\\s*:\\s*(true|false)".toRegex().find(jsonArgs)?.groupValues?.get(1)?.toBoolean() ?: false
        val root = File(project.basePath!!)
        val out = runCommand(listOf("bash","-lc","git fetch --all && git checkout -B $branch"), root)
        if (out.exitCode != 0) return ToolResponse.error(out.stderr)
        if (worktree) {
            val wtDir = File(root.parentFile, "${root.name}-$branch")
            val wo = runCommand(listOf("bash","-lc","git worktree add -B $branch ${wtDir.absolutePath} $branch"), root)
            if (wo.exitCode != 0) return ToolResponse.error(wo.stderr)
            return ToolResponse.ok("""{"ok":true,"worktree":"${wtDir.absolutePath}"}""", "Создан worktree $wtDir")
        }
        return ToolResponse.ok("""{"ok":true}""", "Ветка готова: $branch")
    }
}

class GitCommitTool(project: Project) : ArchitectTool, BaseCmdTool(project) {
    override fun name() = "git_commit"
    override fun description() = "Делает поэтапные коммиты с понятными сообщениями."
    override fun schema() = DeepSeekClient.ToolDef(function =
        DeepSeekClient.FunctionDef(name(), description(), mapOf(
            "type" to "object",
            "properties" to mapOf("message" to mapOf("type" to "string")),
            "required" to listOf("message")
        ))
    )
    override fun invoke(jsonArgs: String): ToolResponse {
        val msg = "\"message\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonArgs)?.groupValues?.get(1) ?: "update"
        val root = File(project.basePath!!)
        val add = runCommand(listOf("bash","-lc","git add -A"), root)
        val commit = runCommand(listOf("bash","-lc","git commit -m \"$msg\" || true"), root)
        return ToolResponse.ok("""{"ok":true}""", (add.stdout + "\n" + commit.stdout).trim())
    }
}

