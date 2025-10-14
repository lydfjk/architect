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

    data class ToolCallResult(val resultJson: String, val humanReadable: String)

    data class ToolExecution(
        val name: String,
        val arguments: String,
        val result: ToolCallResult
    )

    data class ChatResult(
        val reply: String,
        val conversation: List<Msg>,
        val appendedMessages: List<Msg>,
        val toolExecutions: List<ToolExecution>,
        val isFinal: Boolean
    )

    private val http = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(90))
        .build()

    private val moshi = Moshi.Builder().build()
    private val json = "application/json; charset=utf-8".toMediaType()

    fun chat(
        conversation: List<Msg>,
        toolSchemas: List<ToolDef>,
        onToolCall: (name: String, jsonArgs: String) -> ToolCallResult,
        maxIterations: Int = 6
    ): ChatResult {
        val apiKey = SecretStore(ctx).loadApiKey()
        require(!apiKey.isNullOrBlank()) { "DeepSeek API Key не настроен (Settings → Architect)" }

        val settings = ArchitectSettingsService.get(ctx)
        val model = settings.state().model.ifBlank { "deepseek-chat-v3.2" }

        require(conversation.firstOrNull()?.role == "system") {
            "История диалога должна начинаться с системного промпта"
        }

        val workingMessages = conversation.toMutableList()
        val appended = mutableListOf<Msg>()
        val toolLog = mutableListOf<ToolExecution>()

        repeat(maxIterations) {
        val request = ChatRequest(
        model = model,
        temperature = 0.2,
        tool_choice = if (toolSchemas.isEmpty()) null else "auto",
        tools = toolSchemas.takeIf { it.isNotEmpty() },
        messages = workingMessages,
        max_tokens = 2048
            // response_format = mapOf("type" to "json_object") // включай при надобности
        )


            val response = execute(apiKey, request)
            val message = response.choices.firstOrNull()?.message
                ?: return ChatResult("(пусто)", workingMessages, appended, toolLog, false)

            if (!message.content.isNullOrBlank()) {
                val assistantMsg = Msg(role = message.role, content = message.content)
                workingMessages.add(assistantMsg)
                appended.add(assistantMsg)
            }

            val toolCalls = message.tool_calls.orEmpty()
            if (toolCalls.isEmpty()) {
                val reply = appended.lastOrNull { it.role == "assistant" }?.content ?: "(пусто)"
                return ChatResult(reply, workingMessages, appended, toolLog, true)
            }

            for (call in toolCalls) {
                val args = call.function.arguments ?: "{}"
                val toolResult = onToolCall(call.function.name, args)
                val toolMsg = Msg(role = "tool", content = toolResult.resultJson, name = call.function.name)
                workingMessages.add(toolMsg)
                appended.add(toolMsg)
                toolLog.add(ToolExecution(call.function.name, args, toolResult))
            }
        }

        val reply = appended.lastOrNull { it.role == "assistant" }?.content ?: "(пусто)"
        return ChatResult(reply, workingMessages, appended, toolLog, false)
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
    data class ToolDef(
        val type: String = "function",
        val function: FunctionDef
    )

    @JsonClass(generateAdapter = true)
    data class FunctionDef(
        val name: String,
        val description: String,
        @Json(name = "parameters") val jsonSchema: Map<String, Any>,
    )

    @JsonClass(generateAdapter = true)
    data class ChatRequest(
        val model: String,
        val messages: List<Msg>,
        val temperature: Double? = 0.2,
        val top_p: Double? = null,
        val max_tokens: Int? = null,
        val frequency_penalty: Double? = null,
        val presence_penalty: Double? = null,
        val tool_choice: String? = null,          // "auto"
        val tools: List<ToolDef>? = null,
        val response_format: Map<String, String>? = null // e.g., mapOf("type" to "json_object")
    )

    @JsonClass(generateAdapter = true) data class ChatResponse(val choices: List<Choice>)
    @JsonClass(generateAdapter = true) data class Choice(val message: ChoiceMsg)
    @JsonClass(generateAdapter = true) data class ChoiceMsg(
        val role: String,
        val content: String?,
        val tool_calls: List<ToolCall>?,
    )

    @JsonClass(generateAdapter = true) data class ToolCall(
        val id: String,
        val type: String,
        val function: ToolFunction,
    )

    @JsonClass(generateAdapter = true) data class ToolFunction(
        val name: String,
        val arguments: String?,
    )

    companion object {
        fun newConversation(systemPrompt: String): MutableList<Msg> =
            mutableListOf(Msg(role = "system", content = systemPrompt))
    }
}


