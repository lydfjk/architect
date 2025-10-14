package ai.architect.modes

import com.intellij.openapi.components.*

@State(name = "ArchitectAgentMode", storages = [Storage("architect_agent_mode.xml")])
class AgentModeStore : PersistentStateComponent<AgentModeStore.State> {
    data class State(var mode: String = AgentMode.CHAT.name)
    private var state = State()

    override fun getState(): State = state
    override fun loadState(s: State) { state = s }

    fun get(): AgentMode = runCatching { AgentMode.valueOf(state.mode) }.getOrElse { AgentMode.CHAT }
    fun set(m: AgentMode) { state.mode = m.name }
    companion object {
        fun getInstance() = com.intellij.openapi.application.ApplicationManager.getApplication().getService(AgentModeStore::class.java)
    }
}
