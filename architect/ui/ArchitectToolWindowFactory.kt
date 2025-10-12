package ai.architect.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import ai.architect.persona.ArchitectPersona
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*

class ArchitectToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel(BorderLayout())
                val output = JBTextArea().apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            border = JBUI.Borders.empty(8)
        }
        val personas = ArchitectPersona.all()
        val personaBox = ComboBox(personas.toTypedArray()).apply {
            renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<out Any>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component {
                    val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                    val persona = value as? ArchitectPersona
                    if (persona != null) {
                        label.text = "${persona.title} — ${persona.shortLabel}"
                        label.toolTipText = persona.summary
                    } else {
                        label.text = ""
                        label.toolTipText = null
                    }
                    return label
                }
            }
        }
        val personaSummary = JBTextArea().apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            border = JBUI.Borders.empty(8, 8, 0, 8)
            text = personas.firstOrNull()?.summary ?: ""
            foreground = UIManager.getColor("Label.disabledForeground")
            background = panel.background
        }
        val input = JBTextField()
        val send = JButton("Отправить")

        val scroll = JBScrollPane(output)
        val top = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8, 8, 0, 8)
            add(JLabel("Профиль Архитектора:"), BorderLayout.WEST)
            add(personaBox, BorderLayout.CENTER)
        }
        val bottom = JPanel(BorderLayout()).apply {
            add(input, BorderLayout.CENTER)
            add(send, BorderLayout.EAST)
            border = JBUI.Borders.empty(8)
        }

        val north = JPanel(BorderLayout()).apply {
            add(top, BorderLayout.NORTH)
            add(personaSummary, BorderLayout.CENTER)
        }

        panel.add(north, BorderLayout.NORTH)
        panel.add(scroll, BorderLayout.CENTER)
        panel.add(bottom, BorderLayout.SOUTH)

        val content = toolWindow.contentManager.factory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)

        val chat = ArchitectChatController(project) { text ->
            SwingUtilities.invokeLater { output.append("$text\n") }
        }

        personaBox.addActionListener {
            val selected = personaBox.selectedItem as? ArchitectPersona ?: return@addActionListener
            personaSummary.text = selected.summary
            chat.setPersona(selected)
        }

        personaBox.selectedItem = personas.firstOrNull()

        fun sendMessage() {
            val text = input.text.trim()
            if (text.isNotEmpty()) {
                output.append("Вы: $text\n")
                input.text = ""
                chat.sendUserMessage(text)
            }
        }
        send.addActionListener { sendMessage() }
        input.addActionListener { sendMessage() }
    }
}

