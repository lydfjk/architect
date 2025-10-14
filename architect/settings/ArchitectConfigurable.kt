package ai.architect.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class ArchitectConfigurable(private val project: Project) : Configurable {

    private val modelField = JBTextField()
    private val apiBaseField = JBTextField()
    private val apiKeyField = JBPasswordField()
    private val mcpBox = JBCheckBox("Enable MCP bridge")
    private val internetBox = JBCheckBox("Auto-use web tools (search & fetch)")

    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Architect"

    override fun createComponent(): JComponent {
        val store = SecretStore(project)
        apiKeyField.text = store.loadApiKey().orEmpty()

        val s = ArchitectSettingsService.get(project).state
        modelField.text = s.model
        apiBaseField.text = s.apiBase
        mcpBox.isSelected = s.mcpEnabled
        internetBox.isSelected = s.autoInternet

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("DeepSeek model (e.g. deepseek-chat-v3.2):", modelField, 1, false)
            .addLabeledComponent("DeepSeek API base (https://api.deepseek.com/v1):", apiBaseField, 1, false)
            .addLabeledComponent("DeepSeek API Key:", apiKeyField, 1, false)
            .addComponent(mcpBox, 1)
            .addComponent(internetBox, 1)
            .panel
        return panel!!
    }

    override fun isModified(): Boolean {
        val s = ArchitectSettingsService.get(project).state
        return modelField.text != s.model ||
                apiBaseField.text != s.apiBase ||
                mcpBox.isSelected != s.mcpEnabled ||
                internetBox.isSelected != s.autoInternet ||
                apiKeyField.password.concatToString() != SecretStore(project).loadApiKey().orEmpty()
    }

    override fun apply() {
        val svc = ArchitectSettingsService.get(project)
        val s = svc.state
        s.model = modelField.text.trim().ifBlank { "deepseek-chat-v3.2" }
        s.apiBase = apiBaseField.text.trim().ifBlank { "https://api.deepseek.com/v1" }
        s.mcpEnabled = mcpBox.isSelected
        s.autoInternet = internetBox.isSelected
        SecretStore(project).saveApiKey(apiKeyField.password.concatToString())
    }

    override fun reset() {
        val s = ArchitectSettingsService.get(project).state
        modelField.text = s.model
        apiBaseField.text = s.apiBase
        mcpBox.isSelected = s.mcpEnabled
        internetBox.isSelected = s.autoInternet
        apiKeyField.text = SecretStore(project).loadApiKey().orEmpty()
    }

    override fun disposeUIResources() {
        panel = null
    }
}
