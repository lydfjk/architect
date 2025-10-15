package ai.architect.tools

import ai.architect.core.ArchitectTool
import ai.architect.core.DeepSeekClient
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.time.Duration

class WebSearchTool(private val project: com.intellij.openapi.project.Project) : ArchitectTool {
    private val http = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(20)).build()
    private val moshi = Moshi.Builder().build()
    
    override fun name() = "web_search"
    override fun description() = "Ищет ответ в интернете (StackOverflow/StackExchange API)."
    override fun schema() = DeepSeekClient.ToolDef(function =
    DeepSeekClient.FunctionDef(name(), description(), mapOf(
    "type" to "object",
    "properties" to mapOf(
    "query" to mapOf("type" to "string"),
    "top_k" to mapOf("type" to "integer", "default" to 5)
    ),
    "required" to listOf("query")
    ))
    )
    
    override fun invoke(jsonArgs: String): ToolResponse {
        val q = "\"query\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonArgs)?.groupValues?.get(1) ?: return ToolResponse.error("query?")
        val topK = "\"top_k\"\\s*:\\s*(\\d+)".toRegex().find(jsonArgs)?.groupValues?.get(1)?.toInt() ?: 5
        val url = "https://api.stackexchange.com/2.3/search/advanced?order=desc&sort=relevance&site=stackoverflow&filter=!)Q2B_Av2K*&pagesize=$topK&q=" +
        URLEncoder.encode(q, "UTF-8")
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            val items = Regex("\"title\":\"(.*?)\".*?\"link\":\"(.*?)\"").findAll(body)
            .map { it.groupValues[1].replace("\\u003c", "<").replace("\\u003e", ">") to it.groupValues[2] }
                .take(topK).toList()
            val pretty = items.joinToString("\n") { "• ${it.first}\n  ${it.second}" }
            val json = items.joinToString(
            prefix = """{"ok":true,"results":[ """,
            postfix = " ]}"
            ) { """{"title":${it.first.escapeJson()},"url":${it.second.escapeJson()}}""" }
            return ToolResponse.ok(json, pretty)
        }
    }
}
