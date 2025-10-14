package ai.architect.core

import ai.architect.tools.*

class ToolRegistry(private val project: com.intellij.openapi.project.Project) {

    private val tools: Map<String, ArchitectTool> = mapOf(
        "read_file" to ReadFileTool(project),
        "write_file" to WriteFileTool(project),
        "list_files" to ListFilesTool(project),
        "run_gradle" to RunGradleTool(project),
        "run_command" to RunCommandTool(project),
        "git_branch" to GitBranchTool(project),
        "git_commit" to GitCommitTool(project),
        "update_manifest" to UpdateManifestTool(project),
        "generate_icons" to GenerateIconsTool(project),
        "web_search" to WebSearchTool(project),
        "web_search_ddg" to WebSearchDdGTool(project),
        "web_fetch"      to WebFetchTool(project),
        "verify_links"   to VerifyLinksTool(project),
        "github_search"  to GitHubSearchTool(project),
        "apply_patch"    to ApplyPatchTool(project),
        "find_replace"   to FindReplaceTool(project),
        "run_tests"      to RunTestsTool(project),
        "create_pr"     to CreatePrTool(project)
// при неуверенности — обязательный
    )

    fun schemas(): List<DeepSeekClient.ToolDef> = tools.values.map { it.schema() }

    fun call(name: String, jsonArgs: String): ToolResponse {
        val tool = tools[name] ?: return ToolResponse.error("Unknown tool: $name")
        return tool.invoke(jsonArgs)
    }
}

interface ArchitectTool {
    fun name(): String
    fun description(): String
    fun schema(): DeepSeekClient.ToolDef
    fun invoke(jsonArgs: String): ToolResponse
}



