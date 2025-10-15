package ai.architect.core

import ai.architect.tools.ApplyPatchTool
import ai.architect.tools.CreatePrTool
import ai.architect.tools.FindReplaceTool
import ai.architect.tools.GenerateIconsTool
import ai.architect.tools.GitBranchTool
import ai.architect.tools.GitCommitTool
import ai.architect.tools.GitHubSearchTool
import ai.architect.tools.ListFilesTool
import ai.architect.tools.ReadFileTool
import ai.architect.tools.RunCommandTool
import ai.architect.tools.RunGradleTool
import ai.architect.tools.RunTestsTool
import ai.architect.tools.ToolResponse
import ai.architect.tools.UpdateManifestTool
import ai.architect.tools.WebFetchTool
import ai.architect.tools.WebSearchDdGTool
import ai.architect.tools.WebSearchTool
import ai.architect.tools.WriteFileTool
import com.intellij.openapi.project.Project

class ToolRegistry(private val project: Project) {

    private val tools: Map<String, ArchitectTool> = listOf(
        ReadFileTool(project),
        WriteFileTool(project),
        ListFilesTool(project),
        FindReplaceTool(project),
        RunGradleTool(project),
        RunCommandTool(project),
        RunTestsTool(project),
        GitBranchTool(project),
        GitCommitTool(project),
        ApplyPatchTool(project),
        CreatePrTool(project),
        UpdateManifestTool(project),
        GenerateIconsTool(project),
        GitHubSearchTool(project),
        WebSearchTool(project),
        WebSearchDdGTool(project),
        WebFetchTool(project)
    ).associateBy { it.name() }

    fun schemas(): List<DeepSeekClient.ToolDef> =
        tools.values.map { it.schema() }

    fun call(name: String, jsonArgs: String): ToolResponse {
        val tool = tools[name] ?: return ToolResponse.error("Unknown tool: $name")
        return runCatching { tool.invoke(jsonArgs) }
            .onFailure { println("[Architect][ToolRegistry] $name failed: ${it.message}") }
            .getOrElse { ToolResponse.error("Tool $name error: ${it.message ?: it::class.java.simpleName}") }
    }

    fun toolNames(): Set<String> = tools.keys
}

interface ArchitectTool {
    fun name(): String
    fun description(): String
    fun schema(): DeepSeekClient.ToolDef
    fun invoke(jsonArgs: String): ToolResponse
}


