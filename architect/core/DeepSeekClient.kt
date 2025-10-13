package ai.architect.core

import ai.architect.settings.ArchitectSettingsService
import ai.architect.settings.SecretStore
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Duration

class DeepSeekClient(private val ctx: com.intellij.openapi.project.Project) {

    private val http = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(90))
        .build()

    private val moshi = Moshi.Builder().build()
    private val json = "application/json; charset=utf-8".toMediaType()

    // Сигнатуры для tool-calling в стиле OpenAI
    @JsonClass(generateAdapter = true)
    data class ToolDef(
        val type: String = "function",
        val function: FunctionDef
    )
    @JsonClass(generateAdapter = true)
    data class FunctionDef(
        val name: String,
        val description: String,
        @Json(name = "parameters") val jsonSchema: Map<String, Any>
    )

    fun chat(
        userText: String,
        toolSchemas: List<ToolDef>,
        systemPreamble: String,
        onToolCall: (name: String, jsonArgs: String) -> String
    ): String {
        val apiKey = SecretStore(ctx).loadApiKey()
        require(!apiKey.isNullOrBlank()) { "DeepSeek API Key не настроен (Settings → Architect)" }

        // Первый запрос
        val settings = ArchitectSettingsService.get(ctx)
        val model = settings.state().model.ifBlank { "deepseek-chat-v3.2" }

        val req1 = ChatRequest(
            model = model,
            temperature = 0.2,
            tool_choice = "auto",
            tools = toolSchemas,
            messages = listOf(
                Msg(role = "system", content = systemPreamble),
                Msg(role = "user", content = userText)
            )
        )

        val step1 = execute(apiKey, req1)

        // Если модель вызвала инструменты — обработаем детерминированно
        val toolCalls = step1.choices.firstOrNull()?.message?.tool_calls.orEmpty()
        var messages = req1.messages.toMutableList()
        if (toolCalls.isNotEmpty()) {
            for (tc in toolCalls) {
                val args = tc.function.arguments ?: "{}"
                val result = onToolCall(tc.function.name, args)
                messages.add(Msg(role = "tool", name = tc.function.name, content = result))
            }
            // Финальный ответ после tool_result
            val req2 = req1.copy(messages = messages)
            val step2 = execute(apiKey, req2)
            return step2.choices.firstOrNull()?.message?.content ?: "(пусто)"
        }

        return step1.choices.firstOrNull()?.message?.content ?: "(пусто)"
    }

    fun lastWasUncertain(text: String): Boolean {
        val hints = listOf(
            "не уверен", "недостаточно данных", "не могу найти",
            "я не знаю", "ошибка", "unknown", "not sure"
        )
        return hints.any { text.contains(it, ignoreCase = true) }
    }

    private fun execute(apiKey: String, payload: ChatRequest): ChatResponse {
        val body = moshi.adapter(ChatRequest::class.java).toJson(payload).toRequestBody(json)
        val req = Request.Builder()
            .url("https://api.deepseek.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
        http.newCall(req).execute().use { resp ->
            val str = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("DeepSeek HTTP ${resp.code}: $str")
            return moshi.adapter(ChatResponse::class.java).fromJson(str)
                ?: error("Неизвестный ответ DeepSeek")
        }
    }

    // --- DTO (OpenAI-совместимые) ---
    @JsonClass(generateAdapter = true)
    data class Msg(val role: String, val content: String, val name: String? = null)

    @JsonClass(generateAdapter = true)
    data class ChatRequest(
        val model: String,
        val messages: List<Msg>,
        val temperature: Double = 0.2,
        val tools: List<ToolDef>? = null,
        val tool_choice: String? = null,
    )

    @JsonClass(generateAdapter = true) data class ChatResponse(val choices: List<Choice>)
    @JsonClass(generateAdapter = true) data class Choice(val message: ChoiceMsg)
    @JsonClass(generateAdapter = true) data class ChoiceMsg(
        val role: String,
        val content: String?,
        val tool_calls: List<ToolCall>?
    )
    @JsonClass(generateAdapter = true) data class ToolCall(
        val id: String,
        val type: String,
        val function: ToolFunction
    )
    @JsonClass(generateAdapter = true) data class ToolFunction(
        val name: String,
        val arguments: String?
    )
}

