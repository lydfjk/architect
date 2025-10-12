package ai.architect.agents

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

class BackgroundAgentSubmitAction : AnAction("Run Background Agent…") {

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        val title = Messages.showInputDialog(
            project,
            "Короткое название задачи:",
            "Background Agent",
            null
        ) ?: return

        val instruction = Messages.showInputDialog(
            project,
            "Подробная инструкция для агента:",
            "Background Agent",
            null
        ) ?: return

        val priorityStr = Messages.showInputDialog(
            project,
            "Приоритет (целое число, больше — важнее):",
            "Background Agent",
            null,
            "10",
            object : InputValidator {
                override fun checkInput(inputString: String?) =
                    inputString?.toIntOrNull() != null
                override fun canClose(inputString: String?) =
                    inputString?.toIntOrNull() != null
            }
        ) ?: return

        val priority = priorityStr.toInt()

        val svc = project.service<BackgroundAgentService>()
        svc.submit(priority, title, instruction)

        Messages.showInfoMessage(
            project,
            "Задача «$title» отправлена в фон. Приоритет: $priority.",
            "Background Agent"
        )
    }
}