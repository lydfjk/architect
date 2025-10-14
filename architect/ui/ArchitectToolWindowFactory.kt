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
import javax.swing.JButton
import javax.swing.JPanel

class ArchitectToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val output = JBTextArea().apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            border = JBUI.Borders.empty(8)
        }
        val input = JBTextField().apply { emptyText.text = "Спросите Архитектора…" }
        val send = JButton("Send")
        val controller = ArchitectChatController(project) { append ->
            output.append(append)
            output.append("\n")
        }

        val root = JPanel(BorderLayout())
        val south = JPanel(BorderLayout())
        south.add(input, BorderLayout.CENTER)
        south.add(send, BorderLayout.EAST)

        root.add(JBScrollPane(output), BorderLayout.CENTER)
        root.add(south, BorderLayout.SOUTH)

        send.addActionListener {
            val text = input.text.trim()
            if (text.isNotEmpty()) {
                output.append("Вы: $text\n")
                input.text = ""
                controller.sendUserMessage(text)
            }
        }
        input.addActionListener { send.doClick() }

        val content = toolWindow.contentManager.factory.createContent(root, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
