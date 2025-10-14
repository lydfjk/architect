package ai.architect.core

import ai.architect.tools.*
import com.intellij.openapi.project.Project

class ToolRegistry(private val project: Project) {

    private val tools: Map<String, ArchitectTool> = mapOf(
        // файловые операции
        "read_file" to ReadFileTool(project),
        "write_file" to WriteFileTool(project),
        "list_files" to ListFilesTool(project),

        // сборка/запуск
        "run_gradle" to RunGradleTool(project),
        "run_command" to RunCommandTool(project),
        "run_tests" to RunTestsTool(project),

        // git
        "git_branch" to GitBranchTool(project),
        "git_commit" to GitCommitTool(project),

        // веб и поиск
        "web_search" to WebSearchDdGTool(project),
        "web_fetch" to WebFetchTool(project)
    )

    fun allToolDefs(): List<DeepSeekClient.ToolDef> =
        tools.values.map { it.schema() }

    fun invoke(name: String, jsonArgs: String): ToolResponse =
        tools[name]?.invoke(jsonArgs) ?: ToolResponse.error("Unknown tool: $name")
}

interface ArchitectTool {
    fun name(): String
    fun description(): String
    fun schema(): DeepSeekClient.ToolDef
    fun invoke(jsonArgs: String): ToolResponse
}

