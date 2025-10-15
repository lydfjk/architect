package ai.architect.tools

import ai.architect.core.ArchitectTool
import ai.architect.core.DeepSeekClient
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.time.Duration

class WebFetchTool(private val project: com.intellij.openapi.project.Project) : ArchitectTool {
    private val http = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(25))
        .followRedirects(true)
        .build()

    override fun name() = "web_fetch"
    override fun description() = "Загружает страницу по URL и возвращает title + очищенный текст (обрезка по max_chars)."
    override fun schema() = DeepSeekClient.ToolDef(function =
        DeepSeekClient.FunctionDef(
            name(), description(),
            mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "url" to mapOf("type" to "string"),
                    "max_chars" to mapOf("type" to "integer", "default" to 120000)
                ),
                "required" to listOf("url")
            )
        )
    )

    override fun invoke(jsonArgs: String): ToolResponse {
        val url = "\"url\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonArgs)?.groupValues?.get(1)
            ?: return ToolResponse.error("url?")
        val max = "\"max_chars\"\\s*:\\s*(\\d+)".toRegex().find(jsonArgs)?.groupValues?.get(1)?.toInt() ?: 120_000

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Architect/1.0 (+https://example.local)")
            .get().build()
        http.newCall(req).execute().use { resp ->
            val code = resp.code
            val body = resp.body?.string().orEmpty()
            val ct = resp.header("Content-Type").orEmpty()
            val isHtml = ct.contains("html", ignoreCase = true) || body.trimStart().startsWith("<!DOCTYPE")
            val title: String
            val text: String
            if (isHtml) {
                val doc = Jsoup.parse(body)
                title = (doc.title() ?: "").trim()
                text = doc.select("article, main, body").text().take(max)
            } else {
                title = url
                text = body.take(max)
            }
            val pretty = buildString {
                appendLine("[$code] $title")
                appendLine(url)
                appendLine()
                appendLine(text.take(2000))
                if (text.length > 2000) appendLine("… (обрезано)")
            }
            val json = """{"ok":true,"status":$code,"title":${title.escapeJson()},"url":${url.escapeJson()},"content":${text.escapeJson()}}""""
            return ToolResponse.ok(json, pretty)
        }
    }
}
