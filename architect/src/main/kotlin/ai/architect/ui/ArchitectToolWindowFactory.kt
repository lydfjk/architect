package ai.architect.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
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
        val input = JBTextField()
        val send = JButton("Отправить")

        val scroll = JBScrollPane(output)
        val bottom = JPanel(BorderLayout()).apply {
            add(input, BorderLayout.CENTER)
            add(send, BorderLayout.EAST)
            border = JBUI.Borders.empty(8)
        }

        panel.add(scroll, BorderLayout.CENTER)
        panel.add(bottom, BorderLayout.SOUTH)

        val content = toolWindow.contentManager.factory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)

        val chat = ArchitectChatController(project) { text ->
            SwingUtilities.invokeLater { output.append("$text\n") }
        }

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
