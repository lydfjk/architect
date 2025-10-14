package ai.architect.tools

import ai.architect.core.ArchitectTool
import ai.architect.core.DeepSeekClient
import ai.architect.settings.SecretStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder
import java.time.Duration

/**
 * Создаёт (или обновляет) Pull Request через GitHub REST.
 * Если установлен GitHub CLI (`gh`) — может использовать его как fallback.
 *
 * Аргументы JSON:
 * - title: string (обяз.)
 * - body: string (опц.)
 * - base: string (ветка назначения; по умолчанию default_branch репозитория)
 * - head: string (ветка-источник; по умолчанию текущая git-ветка)
 * - draft: boolean (опц., default=false)
 * - labels: [string] (опц.)
 * - assignees: [string] (опц.)
 * - reviewers: [string] (опц., пользователи)
 * - team_reviewers: [string] (опц., команды)
 * - allow_update: boolean (опц., если PR уже существует от этой ветки — обновить заголовок/описание)
 * - use_cli_first: boolean (опц., сначала пробовать `gh pr create`)
 */
class CreatePrTool(
    private val project: com.intellij.openapi.project.Project
) : ArchitectTool, BaseCmdTool(project) {

    private val http = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(30))
        .build()
    private val json = "application/json; charset=utf-8".toMediaType()

    override fun name() = "create_pr"
    override fun description() = "Создаёт Pull Request на GitHub (REST API, с fallback на gh CLI)."
    override fun schema() = DeepSeekClient.ToolDef(function =
        DeepSeekClient.FunctionDef(name(), description(), mapOf(
            "type" to "object",
            "properties" to mapOf(
                "title" to mapOf("type" to "string"),
                "body" to mapOf("type" to "string"),
                "base" to mapOf("type" to "string"),
                "head" to mapOf("type" to "string"),
                "draft" to mapOf("type" to "boolean","default" to false),
                "labels" to mapOf("type" to "array","items" to mapOf("type" to "string")),
                "assignees" to mapOf("type" to "array","items" to mapOf("type" to "string")),
                "reviewers" to mapOf("type" to "array","items" to mapOf("type" to "string")),
                "team_reviewers" to mapOf("type" to "array","items" to mapOf("type" to "string")),
                "allow_update" to mapOf("type" to "boolean","default" to true),
                "use_cli_first" to mapOf("type" to "boolean","default" to false)
            ),
            "required" to listOf("title")
        ))
    )

    override fun invoke(jsonArgs: String): ToolResponse {
        val args = Args.parse(jsonArgs)
        val root = File(project.basePath ?: return ToolResponse.error("no project basePath"))
        val token = SecretStore(project).loadGithubToken() ?: ""

        // Вычисляем owner/repo из origin
        val originUrl = runCommand(listOf("bash","-lc","git remote get-url origin"), root).stdout.trim()
        if (originUrl.isBlank()) return ToolResponse.error("Не найден remote origin.")
        val repo = parseOwnerRepo(originUrl) ?: return ToolResponse.error("Не удалось разобрать owner/repo из origin: $originUrl")
        val owner = repo.substringBefore("/")
        val repoName = repo.substringAfter("/")

        // Текущая ветка (head), если не задана
        val currentBranch = runCommand(listOf("bash","-lc","git rev-parse --abbrev-ref HEAD"), root).stdout.trim()
        val head = (args.head ?: currentBranch).ifBlank { currentBranch }
        if (head.isBlank()) return ToolResponse.error("Не удалось определить head-ветку.")

        // Убедимся, что ветка запушена
        runCommand(listOf("bash","-lc","git rev-parse --verify $head"), root) // локально существует?
        val push = runCommand(listOf("bash","-lc","git ls-remote --heads origin ${escape(head)}"), root)
        if (push.stdout.isBlank()) {
            runCommand(listOf("bash","-lc","git push -u origin ${escape(head)}"), root)
        }

        // Выясним base (default_branch если не передали)
        val base = args.base ?: run {
            val repoInfo = apiGet("https://api.github.com/repos/$owner/$repoName", token)
            val def = "\"default_branch\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(repoInfo.body)?.groupValues?.get(1)
            def ?: "main" // запасной вариант
        }

        // Возможный вариант: сначала через gh CLI (если просили и он есть)
        if (args.useCliFirst && hasGh()) {
            val cliRes = ghCreatePr(owner, repoName, head, base, args, root)
            if (cliRes != null) return cliRes
        }

        // Создаём PR через REST
        val createJson = buildString {
            append("{")
            append("\"title\":${args.title.escapeJson()},")
            append("\"head\":${head.escapeJson()},")
            append("\"base\":${base.escapeJson()},")
            if (!args.body.isNullOrBlank()) append("\"body\":${args.body.escapeJson()},")
            append("\"draft\":${args.draft}")
            append("}")
        }
        val create = apiPost("https://api.github.com/repos/$owner/$repoName/pulls", token, createJson)
        if (create.code == 201) {
            val num = "\"number\"\\s*:\\s*(\\d+)".toRegex().find(create.body)?.groupValues?.get(1)?.toInt() ?: -1
            val html = "\"html_url\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(create.body)?.groupValues?.get(1).orEmpty()

            // labels / assignees
            if (num > 0) {
                if (args.labels.isNotEmpty()) {
                    val labelsJson = """{"labels":[${args.labels.joinToString(","){ it.escapeJson() }}]}"""
                    apiPost("https://api.github.com/repos/$owner/$repoName/issues/$num/labels", token, labelsJson)
                }
                if (args.assignees.isNotEmpty()) {
                    val assigneesJson = """{"assignees":[${args.assignees.joinToString(","){ it.escapeJson() }}]}"""
                    apiPost("https://api.github.com/repos/$owner/$repoName/issues/$num/assignees", token, assigneesJson)
                }
                if (args.reviewers.isNotEmpty() || args.teamReviewers.isNotEmpty()) {
                    val reviewersJson = buildString {
                        append("{")
                        if (args.reviewers.isNotEmpty())
                            append("\"reviewers\":[${args.reviewers.joinToString(","){ it.escapeJson() }}]")
                        if (args.teamReviewers.isNotEmpty()) {
                            if (args.reviewers.isNotEmpty()) append(",")
                            append("\"team_reviewers\":[${args.teamReviewers.joinToString(","){ it.escapeJson() }}]")
                        }
                        append("}")
                    }
                    apiPost("https://api.github.com/repos/$owner/$repoName/pulls/$num/requested_reviewers", token, reviewersJson)
                }
            }

            val pretty = "✅ PR создан: $html (№$num)\nbase=$base, head=$head"
            val json = """{"ok":true,"url":${html.escapeJson()},"number":$num,"base":${base.escapeJson()},"head":${head.escapeJson()}}"""
            return ToolResponse.ok(json, pretty)
        }

        // Если PR уже существует и allow_update — пробуем найти и обновить
        if (args.allowUpdate && create.code == 422) {
            val qHead = URLEncoder.encode("$owner:$head", "UTF-8")
            val list = apiGet("https://api.github.com/repos/$owner/$repoName/pulls?state=open&head=$qHead", token)
            val num = "\"number\"\\s*:\\s*(\\d+)".toRegex().find(list.body)?.groupValues?.get(1)?.toInt() ?: -1
            if (num > 0) {
                val patch = buildString {
                    append("{")
                    append("\"title\":${args.title.escapeJson()}")
                    if (!args.body.isNullOrBlank()) append(",\"body\":${args.body.escapeJson()}")
                    append("}")
                }
                val upd = apiPatch("https://api.github.com/repos/$owner/$repoName/pulls/$num", token, patch)
                val html = "\"html_url\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(upd.body)?.groupValues?.get(1).orEmpty()
                val pretty = "♻️ PR обновлён: $html (№$num)\nbase=$base, head=$head"
                val json = """{"ok":true,"updated":true,"url":${html.escapeJson()},"number":$num}"""
                return ToolResponse.ok(json, pretty)
            }
        }

        // Попробуем gh как запасной путь
        if (hasGh()) {
            val cliRes = ghCreatePr(owner, repoName, head, base, args, root)
            if (cliRes != null) return cliRes
        }

        val msg = "GitHub REST error ${create.code}:\n${create.body.take(4000)}"
        return ToolResponse.error(msg)
    }

    // ---------- Helpers ----------

    private fun parseOwnerRepo(url: String): String? {
        // https://github.com/owner/repo.git  -> owner/repo
        // git@github.com:owner/repo.git     -> owner/repo
        val cleaned = url.trim().removeSuffix(".git")
        return when {
            cleaned.startsWith("http") -> cleaned.substringAfter("github.com/").takeIf { it.contains("/") }
            cleaned.startsWith("git@") -> cleaned.substringAfter(":").takeIf { it.contains("/") }
            else -> null
        }
    }

    private fun hasGh(): Boolean {
        val res = runCommand(listOf("bash","-lc","command -v gh >/dev/null 2>&1; echo $?"), File(project.basePath!!))
        return res.stdout.trim() == "0"
    }

    private fun ghCreatePr(owner: String, repo: String, head: String, base: String, a: Args, cwd: File): ToolResponse? {
        val flags = mutableListOf<String>()
        flags += listOf("gh","pr","create","-R","$owner/$repo","-H", head,"-B", base,"-t", a.title)
        if (!a.body.isNullOrBlank()) flags += listOf("-b", a.body!!)
        if (a.draft) flags += "-d"
        if (a.labels.isNotEmpty()) flags += listOf("-l", a.labels.joinToString(","))
        if (a.assignees.isNotEmpty()) flags += listOf("-a", a.assignees.joinToString(","))
        if (a.reviewers.isNotEmpty()) flags += listOf("-r", a.reviewers.joinToString(","))
        // team_reviewers у gh указываются как org/team через тот же -r
        if (a.teamReviewers.isNotEmpty()) flags += listOf("-r", a.teamReviewers.joinToString(","))
        val out = runCommand(flags, cwd)
        if (out.exitCode == 0) {
            val url = out.stdout.lines().firstOrNull { it.contains("github.com/") }.orEmpty()
            val pretty = "✅ PR создан через gh: $url"
            val json = """{"ok":true,"cli":true,"url":${url.escapeJson()}}"""
            return ToolResponse.ok(json, pretty)
        }
        return null
    }

    private data class HttpRes(val code: Int, val body: String)

    private fun apiGet(url: String, token: String): HttpRes {
        val req = Request.Builder().url(url)
            .header("Accept","application/vnd.github+json")
            .header("X-GitHub-Api-Version","2022-11-28")
            .apply { if (token.isNotBlank()) header("Authorization","Bearer $token") }
            .get().build()
        http.newCall(req).execute().use { r -> return HttpRes(r.code, r.body?.string().orEmpty()) }
    }
    private fun apiPost(url: String, token: String, jsonBody: String): HttpRes {
        val req = Request.Builder().url(url)
            .header("Accept","application/vnd.github+json")
            .header("X-GitHub-Api-Version","2022-11-28")
            .apply { if (token.isNotBlank()) header("Authorization","Bearer $token") }
            .post(jsonBody.toRequestBody(json)).build()
        http.newCall(req).execute().use { r -> return HttpRes(r.code, r.body?.string().orEmpty()) }
    }
    private fun apiPatch(url: String, token: String, jsonBody: String): HttpRes {
        val req = Request.Builder().url(url)
            .header("Accept","application/vnd.github+json")
            .header("X-GitHub-Api-Version","2022-11-28")
            .apply { if (token.isNotBlank()) header("Authorization","Bearer $token") }
            .patch(jsonBody.toRequestBody(json)).build()
        http.newCall(req).execute().use { r -> return HttpRes(r.code, r.body?.string().orEmpty()) }
    }

    private object Args {
        fun parse(src: String): P {
            fun s(key: String): String? = """"$key"\s*:\s*"(.*?)"""".toRegex(RegexOption.DOT_MATCHES_ALL).find(src)?.groupValues?.get(1)
            fun b(key: String, def: Boolean) = """"$key"\s*:\s*(true|false)""".toRegex().find(src)?.groupValues?.get(1)?.toBoolean() ?: def
            fun arr(key: String): List<String> {
                val m = """"$key"\s*:\s*\[(.*?)\]""".toRegex(RegexOption.DOT_MATCHES_ALL).find(src)?.groupValues?.get(1) ?: return emptyList()
                return """\"(.*?)\"""".toRegex().findAll(m).map { it.groupValues[1] }.toList()
            }
            return P(
                title = s("title") ?: "",
                body = s("body"),
                base = s("base"),
                head = s("head"),
                draft = b("draft", false),
                labels = arr("labels"),
                assignees = arr("assignees"),
                reviewers = arr("reviewers"),
                teamReviewers = arr("team_reviewers"),
                allowUpdate = b("allow_update", true),
                useCliFirst = b("use_cli_first", false)
            )
        }
        data class P(
            val title: String,
            val body: String?,
            val base: String?,
            val head: String?,
            val draft: Boolean,
            val labels: List<String>,
            val assignees: List<String>,
            val reviewers: List<String>,
            val teamReviewers: List<String>,
            val allowUpdate: Boolean,
            val useCliFirst: Boolean
        )
    }
}

// JSON-утилиты
private fun String.escapeJson(): String =
    "\"" + this.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
private fun escape(s: String) = s.replace("\"","\\\"")
