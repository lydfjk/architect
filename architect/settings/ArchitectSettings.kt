package ai.architect.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.service

@State(name = "ArchitectSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
@Service(Service.Level.PROJECT)
class ArchitectSettingsService : PersistentStateComponent<ArchitectSettingsService.State> {

    data class State(
        var model: String = "deepseek-chat-v3.2",
        var apiBase: String = "https://api.deepseek.com/v1",
        var mcpEnabled: Boolean = true,
        var autoInternet: Boolean = true
    )

    private var myState = State()
    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    companion object {
        fun get(project: Project): ArchitectSettingsService = project.service()
    }
}
