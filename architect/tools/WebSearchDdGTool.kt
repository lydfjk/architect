package ai.architect.tools

import ai.architect.core.ArchitectTool
import ai.architect.core.DeepSeekClient
import com.intellij.openapi.project.Project
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.time.Duration

class WebSearchDdGTool(private val project: Project) : ArchitectTool {
    private val http = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(20))
        .followRedirects(true)
        .build()

    override fun name() = "web_search"
    override fun description() = "Ищет в интернете через DuckDuckGo (без API‑ключа). Аргументы: query (string), limit (int)."

    override fun schema() = DeepSeekClient.ToolDef(
        function = DeepSeekClient.FunctionDef(
            name = name(),
            description = description(),
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "query" to mapOf("type" to "string"),
                    "limit" to mapOf("type" to "integer", "default" to 5)
                ),
                "required" to listOf("query")
            )
        )
    )

    override fun invoke(jsonArgs: String): ToolResponse {
        val q = "\"query\"\\s*:\\s*\"([\\s\\S]*?)\"".toRegex().find(jsonArgs)?.groupValues?.get(1)?.unescapeJson()
            ?: return ToolResponse.error("query required")
        val limit = "\"limit\"\\s*:\\s*(\\d+)".toRegex().find(jsonArgs)?.groupValues?.get(1)?.toIntOrNull() ?: 5

        val url = "https://duckduckgo.com/html/?q=" + java.net.URLEncoder.encode(q, "UTF-8")
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { resp ->
            val html = resp.body?.string().orEmpty()
            val doc = Jsoup.parse(html)
            val items = doc.select("a.result__a, a.result__url")
                .mapNotNull { a ->
                    val raw = a.attr("href")
                    val decoded = decodeDuckLink(raw) ?: return@mapNotNull null
                    val title = a.text().ifBlank { decoded }
                    title to decoded
                }.distinct()
                .take(limit)

            val pretty = buildString {
                appendLine("DuckDuckGo results for: $q")
                items.forEachIndexed { i, (title, link) ->
                    appendLine("${i+1}. $title")
                    appendLine(link)
                    appendLine()
                }
            }
            val payload = items.joinToString(",") { (t, l) ->
                """{"title":${t.escapeJson()},"url":${l.escapeJson()}}"""
            }
            return ToolResponse.ok("""{"ok":true,"items":[$payload]}""", pretty)
        }
    }

    private fun decodeDuckLink(href: String): String? {
        // ссылки DDG вида https://duckduckgo.com/l/?uddg=...
        val idx = href.indexOf("uddg=")
        if (idx < 0) return if (href.startsWith("http")) href else null
        val enc = href.substring(idx + 5).split("&", limit = 2).first()
        return runCatching { URLDecoder.decode(enc, "UTF-8") }.getOrNull()
    }
}

private fun String.escapeJson(): String = "\"" + this.replace("\\","\\\\").replace("\"","\\\"") + "\""
private fun String.unescapeJson(): String = this.replace("\\\"","\"").replace("\\\\","\\")
