package ai.architect.tools

import ai.architect.core.DeepSeekClient

fun ToolResponse.toToolCallResult(): DeepSeekClient.ToolCallResult =
    DeepSeekClient.ToolCallResult(json, humanReadable)
