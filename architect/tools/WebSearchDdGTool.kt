package ai.architect.tools

import ai.architect.core.ArchitectTool
import ai.architect.core.DeepSeekClient
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Duration

class WebSearchDdGTool(private val project: com.intellij.openapi.project.Project) : ArchitectTool {
    private val http = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(20))
        .followRedirects(true)
        .build()

    override fun name() = "web_search_ddg"
    override fun description() = "Поиск в интернете через DuckDuckGo (HTML endpoint), без ключей."
    override fun schema() = DeepSeekClient.ToolDef(function =
        DeepSeekClient.FunctionDef(
            name(), description(),
            mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "query" to mapOf("type" to "string"),
                    "top_k" to mapOf("type" to "integer", "default" to 5)
                ),
                "required" to listOf("query")
            )
        )
    )

    override fun invoke(jsonArgs: String): ToolResponse {
        val q = "\"query\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonArgs)?.groupValues?.get(1)
            ?: return ToolResponse.error("query?")
        val topK = "\"top_k\"\\s*:\\s*(\\d+)".toRegex().find(jsonArgs)?.groupValues?.get(1)?.toInt() ?: 5
        val url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(q, "UTF-8")

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Architect/1.0 (+https://example.local)")
            .get()
            .build()

        http.newCall(req).execute().use { resp ->
            val html = resp.body?.string().orEmpty()
            val doc = Jsoup.parse(html)
            val results = doc.select("a.result__a, div.result a[href]")
                .mapNotNull { a ->
                    val title = a.text().trim()
                    val href = a.attr("href")
                    val finalUrl = decodeDuckLink(href) ?: href
                    if (title.isNotBlank() && finalUrl.startsWith("http")) title to finalUrl else null
                }
                .distinctBy { it.second }
                .take(topK)
            val pretty = results.joinToString("\n") { "• ${it.first}\n  ${it.second}" }
            val json = results.joinToString(prefix = """{"ok":true,"results":[ """, postfix = " ]}") {
                """{"title":${it.first.escapeJson()},"url":${it.second.escapeJson()}}"""
            }
            return ToolResponse.ok(json, pretty.ifBlank { "Ничего не найдено." })
        }
    }

    private fun decodeDuckLink(href: String): String? {
        // ссылки DDG вида https://duckduckgo.com/l/?uddg=...
        val idx = href.indexOf("uddg=")
        if (idx < 0) return null
        val enc = href.substring(idx + 5).split("&", limit = 2).first()
        return runCatching { URLDecoder.decode(enc, "UTF-8") }.getOrNull()
    }
}

// лок. утилиты (копии из других файлов при необходимости)
private fun String.escapeJson(): String =
    "\"" + this.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
