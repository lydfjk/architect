package ai.architect.security

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.application.ApplicationManager

class QuickMobileAuditAction : AnAction("Quick Mobile Audit") {

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return

        ProgressManager.getInstance().run(object :
            Task.Backgroundable(project, "Quick Mobile Audit", false) {

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Сканирую проект…"

                val results = QuickMobileAudit(project).run()

                val message =
                    if (results.isEmpty()) "✅ Ничего критичного не найдено."
                    else results.joinToString("\n") {
                        "• [${it.severity}] ${it.file}: ${it.message}"
                    }

                ApplicationManager.getApplication().invokeLater {
                    Messages.showInfoMessage(project, message, "Quick Mobile Audit")
                }
            }
        })
    }
}
