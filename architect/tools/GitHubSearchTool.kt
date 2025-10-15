package ai.architect.tools

import ai.architect.core.ArchitectTool
import ai.architect.core.DeepSeekClient
import ai.architect.settings.SecretStore
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.time.Duration

class GitHubSearchTool(private val project: com.intellij.openapi.project.Project) : ArchitectTool {
    private val http = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(20)).build()

    override fun name() = "github_search"
    override fun description() = "GitHub Search API: issues/PR, code, repos. Требует GITHUB_TOKEN для высоких квот."
    override fun schema() = DeepSeekClient.ToolDef(function =
        DeepSeekClient.FunctionDef(name(), description(), mapOf(
            "type" to "object",
            "properties" to mapOf(
                "mode" to mapOf("type" to "string", "enum" to listOf("issues", "code", "repos"), "default" to "issues"),
                "query" to mapOf("type" to "string"),
                "per_page" to mapOf("type" to "integer", "default" to 10)
            ),
            "required" to listOf("query")
        ))
    )

    override fun invoke(jsonArgs: String): ToolResponse {
        val mode = "\"mode\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonArgs)?.groupValues?.get(1) ?: "issues"
        val q = "\"query\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonArgs)?.groupValues?.get(1)
            ?: return ToolResponse.error("query?")
        val per = "\"per_page\"\\s*:\\s*(\\d+)".toRegex().find(jsonArgs)?.groupValues?.get(1)?.toInt() ?: 10

        val endpoint = when (mode) {
            "code" -> "https://api.github.com/search/code"
            "repos" -> "https://api.github.com/search/repositories"
            else -> "https://api.github.com/search/issues"
        }
        val url = "$endpoint?q=" + URLEncoder.encode(q, "UTF-8") + "&per_page=$per"

        val token = SecretStore(project).loadGithubToken()
        val req = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
            .get().build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            val ok = resp.isSuccessful
            val pretty = if (ok) "GitHub search ok ($mode): $url" else "GitHub search error ${resp.code}: $body"
            val json = """{"ok":$ok,"status":${resp.code},"body":${body.escapeJson()}}"""
            return if (ok) ToolResponse.ok(json, pretty) else ToolResponse.error(pretty)
        }
    }
}
