package ai.architect.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import javax.swing.*

@State(name = "ArchitectSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class ArchitectSettingsService : PersistentStateComponent<ArchitectSettingsService.State> {
    data class State(
        var model: String = "deepseek-chat-v3.2",
        var mcpEnabled: Boolean = true
    )

    private var myState = State()

    val state: State
    fun state(): State = myState

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }
    companion object {
        fun get(project: Project) = project.service<ArchitectSettingsService>()
    }
}

class ArchitectConfigurable(private val project: Project) : Configurable {
    private val modelField = JBTextField()
    private val apiField = JBPasswordField()
    private val mcpBox = JCheckBox("Включить MCP мост", true)
    private val panel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(JLabel("DeepSeek model (напр. deepseek-chat)"))
        add(modelField)
        add(JLabel("DeepSeek API Key (хранится в PasswordSafe)"))
        add(apiField)
        add(mcpBox)
    }

    override fun getDisplayName() = "Architect"
    override fun createComponent() = panel
    override fun isModified(): Boolean {
        val state = ArchitectSettingsService.get(project).state()
        val modelChanged = modelField.text.trim() != state.model
        val mcpChanged = mcpBox.isSelected != state.mcpEnabled
        val apiProvided = apiField.password.isNotEmpty()
        return modelChanged || mcpChanged || apiProvided
    }

    override fun apply() {
        val s = ArchitectSettingsService.get(project)
        s.state().model = modelField.text.trim().ifEmpty { "deepseek-chat-v3.2" }
        s.state().mcpEnabled = mcpBox.isSelected
        SecretStore(project).saveApiKey(String(apiField.password))
        apiField.text = ""
    }

    override fun reset() {
        val s = ArchitectSettingsService.get(project)␊
        modelField.text = s.state().model
        mcpBox.isSelected = s.state().mcpEnabled
        apiField.text = ""
    }
}


