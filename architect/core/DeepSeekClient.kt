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
import com.intellij.openapi.project.Project

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

    data class ChatResult(
        val content: String?,
        val toolCalls: List<ToolCall>
    )

    private val moshi = Moshi.Builder().build()
    private val client = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(60))
        .build()

    fun chat(messages: List<Msg>, tools: List<ToolDef>? = null): ChatResult {
        val svc = ArchitectSettingsService.get(project).state
        val apiKey = SecretStore(project).loadApiKey()
            ?: error("DeepSeek API key is not set (Settings → Tools → Architect).")
        val req = ChatRequest(
            model = svc.model,
            messages = messages,
            tools = tools,
            toolChoice = "auto",
            temperature = 0.2,
            // JSON‑режим включайте по месту: responseFormat = mapOf("type" to "json_object")
        )
        val adapter = moshi.adapter(ChatRequest::class.java)
        val json = adapter.toJson(req)

        val url = svc.apiBase.trimEnd('/') + "/chat/completions"
        val httpReq = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(httpReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("DeepSeek API error: HTTP ${resp.code}\n$body")
            }
            val parsed = moshi.adapter(ChatResponse::class.java).fromJson(body)
                ?: error("Empty DeepSeek response")

            val msg = parsed.choices.firstOrNull()?.message
            return ChatResult(
                content = msg?.content,
                toolCalls = msg?.toolCalls ?: emptyList()
            )
        }
    }

    companion object {
        fun newConversation(systemPrompt: String): MutableList<Msg> =
            mutableListOf(Msg(role = "system", content = systemPrompt))
    }
}
