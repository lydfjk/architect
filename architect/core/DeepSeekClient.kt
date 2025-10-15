package ai.architect.core

import ai.architect.settings.ArchitectSettingsService
import ai.architect.settings.SecretStore
import com.intellij.openapi.project.Project
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Duration
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class DeepSeekClient(private val project: Project) {

    @JsonClass(generateAdapter = true)
    data class Msg(
        val role: String,
        val content: String? = null,
        @Json(name = "tool_calls") val toolCalls: List<ToolCall>? = null,
        @Json(name = "tool_call_id") val toolCallId: String? = null,
        val name: String? = null
    )

    @JsonClass(generateAdapter = true)
    data class ToolCall(
        val id: String? = null,
        val type: String = "function",
        val function: ToolFunction
    )

    @JsonClass(generateAdapter = true)
    data class ToolFunction(
        val name: String,
        val arguments: String?
    )

    @JsonClass(generateAdapter = true)
    data class FunctionDef(
        val name: String,
        val description: String,
        val parameters: Map<String, Any?>
    )

    @JsonClass(generateAdapter = true)
    data class ToolDef(
        val type: String = "function",
        val function: FunctionDef
    )

    @JsonClass(generateAdapter = true)
    data class ChatRequest(
        val model: String,
        val messages: List<Msg>,
        val temperature: Double? = 0.2,
        val tools: List<ToolDef>? = null,
        @Json(name = "tool_choice") val toolChoice: String? = "auto",
        @Json(name = "response_format") val responseFormat: Map<String, String>? = null,
        @Json(name = "max_tokens") val maxTokens: Int? = null,
        val stream: Boolean? = false
    )

    @JsonClass(generateAdapter = true)
    data class ChoiceMessage(
        val role: String,
        val content: String?,
        @Json(name = "tool_calls") val toolCalls: List<ToolCall>?
    )

    @JsonClass(generateAdapter = true)
    data class Choice(
        val index: Int,
        val message: ChoiceMessage
    )

    @JsonClass(generateAdapter = true)
    data class ChatResponse(
        val id: String,
        val choices: List<Choice>
    )

    data class ToolCallResult(
        val outputJson: String,
        val humanReadable: String
    )

    data class ChatResult(
        val conversation: List<Msg>,
        val appendedMessages: List<Msg>,
        val reply: String?
    )

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val client = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(120))
        .build()

    fun chat(
        conversation: List<Msg>,
        toolSchemas: List<ToolDef>? = null,
        onToolCall: ((String, String) -> ToolCallResult)? = null,
        maxToolLoops: Int = 4
    ): ChatResult {
        val current = conversation.toMutableList()
        val appended = mutableListOf<Msg>()
        var loops = 0

        while (true) {
            val response = execute(current, toolSchemas)
            val message = response.choices.firstOrNull()?.message
                ?: return ChatResult(current, appended, appended.lastOrNull { it.role == "assistant" }?.content)

            val assistantMsg = Msg(
                role = message.role,
                content = message.content,
                toolCalls = message.toolCalls
            )
            current.add(assistantMsg)
            appended += assistantMsg

            val toolCalls = message.toolCalls.orEmpty()
            if (toolCalls.isEmpty()) {
                return ChatResult(current, appended, assistantMsg.content)
            }
            if (onToolCall == null) {
                throw IllegalStateException("Model requested tool execution but handler is null")
            }
            loops += 1
            if (loops > maxToolLoops) {
                return ChatResult(current, appended, assistantMsg.content)
            }

            val toolMessages = toolCalls.map { call ->
                val args = call.function.arguments ?: "{}"
                val result = onToolCall.invoke(call.function.name, args)
                Msg(
                    role = "tool",
                    content = result.outputJson,
                    toolCallId = call.id,
                    name = call.function.name
                )
            }
            toolMessages.forEach { appended += it }
            current.addAll(toolMessages)
        }
    }

    private fun execute(messages: List<Msg>, tools: List<ToolDef>?): ChatResponse {
        val settings = ArchitectSettingsService.get(project).state
        val apiKey = SecretStore(project).loadApiKey()
            ?: error("DeepSeek API key is not set (Settings → Tools → Architect).")

        val request = ChatRequest(
            model = settings.model,
            messages = messages,
            tools = tools?.takeIf { it.isNotEmpty() },
            toolChoice = if (!tools.isNullOrEmpty()) "auto" else null,
            temperature = 0.2
        )
        val json = moshi.adapter(ChatRequest::class.java).toJson(request)

        val url = settings.apiBase.trimEnd('/') + "/chat/completions"
        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("DeepSeek API error: HTTP ${response.code}\n$body")
            }
            return moshi.adapter(ChatResponse::class.java).fromJson(body)
                ?: error("Empty DeepSeek response")
        }
    }

    fun chatOnce(system: String, user: String): String {
        val convo = newConversation(system)
        convo.add(Msg(role = "user", content = user))
        val result = chat(convo)
        return result.reply.orEmpty()
    }

    fun lastWasUncertain(reply: String?): Boolean {
        val text = reply?.lowercase()?.trim() ?: return true
        if (text.isBlank()) return true
        val patterns = listOf(
            "не уверен",
            "не могу",
            "не удалось",
            "не нашел",
            "не нашёл",
            "i'm not sure",
            "i am not sure",
            "not sure",
            "don't know",
            "do not know",
            "unable to",
            "cannot",
            "can't help"
        )
        return patterns.any { it in text }
    }

    companion object {
        fun newConversation(systemPrompt: String): MutableList<Msg> =
            mutableListOf(Msg(role = "system", content = systemPrompt))
    }
}

