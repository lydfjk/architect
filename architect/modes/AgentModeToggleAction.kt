package ai.architect.modes

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

class AgentModeToggleAction : AnAction("Architect: Toggle Agent Mode") {
    private val order = listOf(AgentMode.CHAT, AgentMode.APPLY, AgentMode.RUN, AgentMode.AUTO)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val store = AgentModeStore.getInstance()
        val current = store.get()
        val next = order[(order.indexOf(current) + 1) % order.size]
        store.set(next)
        notify(project, "Architect режим: ${next.title} — ${next.hint}")
    }

    private fun notify(project: Project, msg: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Architect")
            .createNotification(msg, NotificationType.INFORMATION)
            .notify(project)
    }
}