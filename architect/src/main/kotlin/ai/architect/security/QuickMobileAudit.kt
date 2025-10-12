package ai.architect.security

import com.intellij.openapi.project.Project
import java.io.File

class QuickMobileAudit(private val project: Project) {

    data class Finding(val severity: String, val file: String, val message: String)

    fun run(): List<Finding> {
        val basePath = project.basePath ?: return emptyList()
        val base = File(basePath)
        if (!base.exists()) return emptyList()

        val findings = mutableListOf<Finding>()
        val ignore = setOf("build", ".gradle", ".idea", ".git")

        base.walkTopDown()
            .onEnter { dir -> dir.name !in ignore } // пропускаем служебные папки
            .forEach { f ->
                if (f.isFile && f.extension in listOf("xml", "kt", "java")) {
                    val text = runCatching { f.readText(Charsets.UTF_8) }.getOrElse { "" }

                    if (f.name == "AndroidManifest.xml") {
                        if (text.contains("android:debuggable=\"true\""))
                            findings += Finding("HIGH", f.path, "debuggable=true")
                        if (text.contains("android:allowBackup=\"true\""))
                            findings += Finding("MEDIUM", f.path, "allowBackup=true")
                        if (text.contains("usesCleartextTraffic=\"true\""))
                            findings += Finding("MEDIUM", f.path, "cleartextTraffic разрешен")
                    }
                    if (text.contains("addJavascriptInterface("))
                        findings += Finding("HIGH", f.path, "WebView addJavascriptInterface обнаружен")
                    if (text.contains("setJavaScriptEnabled(true)"))
                        findings += Finding("MEDIUM", f.path, "WebView JS включен")
                }
            }
        return findings
    }
}
