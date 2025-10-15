package ai.architect.playbooks

import ai.architect.core.ToolRegistry
import ai.architect.tools.ToolResponse
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.messages.MessagesService

class RunPlaybookAction : AnAction(
    "Run Playbook…",
    "Запустить один из плейбуков (XML→Compose и др.)",
    null
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val items = PlaybookRegistry.all()
        if (items.isEmpty()) {
            notify(project, "Нет доступных плейбуков", NotificationType.WARNING)
            return
        }

        // ВАЖНО: используем перегрузку showChooseDialog с project и icon
        val titles = items.map { it.title }.toTypedArray()

        @Suppress("DEPRECATION")
        val idx: Int = Messages.showChooseDialog(
            /* project      = */ project,
            /* message      = */ "Выберите плейбук для запуска",
            /* title        = */ "Architect — Playbooks",
            /* icon         = */ Messages.getQuestionIcon(),
            /* values       = */ titles,
            /* initialValue = */ titles.first()
        )

        if (idx < 0) return // пользователь нажал Cancel

        val playbook = items[idx]

        // Реальный ToolRegistry:
        val registry = ToolRegistry(project)

        // Адаптер под сигнатуру плейбуков: (name, argsJson) -> ToolResponse
        val tools: (String, String) -> ToolResponse = { name, argsJson ->
            registry.call(name, argsJson)
        }

        val result = runCatching { playbook.run(project, tools) }
            .getOrElse { ex -> "Ошибка при выполнении плейбука: ${ex.message ?: ex}" }

        notify(project, "Плейбук «${playbook.title}»: $result", NotificationType.INFORMATION)
    }

    private fun notify(project: Project, msg: String, type: NotificationType) {
        Notifications.Bus.notify(
            Notification("Architect", "Architect", msg, type),
            project
        )
    }
}



