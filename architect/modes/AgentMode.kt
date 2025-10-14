package ai.architect.modes

enum class AgentMode(val title: String, val hint: String) {
    CHAT("Chat", "Только диалог, без автоприменения"),
    APPLY("Apply", "Применять найденные диффы/правки"),
    RUN("Run", "После правок запускать тесты/команды"),
    AUTO("Auto", "План → правки → тесты → PR")
}
