package ai.architect.tools

import ai.architect.core.ArchitectTool
import ai.architect.core.DeepSeekClient
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import java.awt.Image
import java.io.File
import javax.imageio.ImageIO

class UpdateManifestTool(private val project: Project) : ArchitectTool {
    override fun name() = "update_manifest"
    override fun description() = "Правит AndroidManifest.xml: package, разрешения, activity/intent-filter."
    override fun schema() = DeepSeekClient.ToolDef(function =
        DeepSeekClient.FunctionDef(name(), description(), mapOf(
            "type" to "object",
            "properties" to mapOf(
                "path" to mapOf("type" to "string"),
                "setPackage" to mapOf("type" to "string"),
                "permissions" to mapOf("type" to "array","items" to mapOf("type" to "string"))
            ),
            "required" to listOf("path")
        ))
    )
    override fun invoke(jsonArgs: String): ToolResponse {
        val path = "\"path\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonArgs)?.groupValues?.get(1) ?: "app/src/main/AndroidManifest.xml"
        val base = File(project.basePath!!)
        val file = File(base, path)
        if (!file.exists()) return ToolResponse.error("Manifest not found: $path")
        val setPkg = "\"setPackage\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonArgs)?.groupValues?.get(1)
        val perms = "\"permissions\"\\s*:\\s*\\[(.*)]".toRegex(RegexOption.DOT_MATCHES_ALL).find(jsonArgs)?.groupValues?.get(1)
            ?.split(",")?.map { it.trim().trim('"') }?.filter { it.isNotBlank() } ?: emptyList()
        WriteCommandAction.runWriteCommandAction(project) {
            var text = file.readText()
            if (!setPkg.isNullOrBlank()) {
                text = text.replace(Regex("package\\s*=\\s*\"[^\"]*\""), "package=\"$setPkg\"")
            }
            if (perms.isNotEmpty() && !text.contains("uses-permission")) {
                val permsStr = perms.joinToString("\n") { """    <uses-permission android:name="$it" />""" }
                text = text.replaceFirst("<application", "$permsStr\n    <application")
            }
            file.writeText(text)
        }
        return ToolResponse.ok("""{"ok":true}""", "Манифест обновлён")
    }
}

class GenerateIconsTool(private val project: Project) : ArchitectTool {
    override fun name() = "generate_icons"
    override fun description() = "Генерирует launcher иконки из картинки и раскладывает по mipmap-*."
    override fun schema() = DeepSeekClient.ToolDef(function =
        DeepSeekClient.FunctionDef(name(), description(), mapOf(
            "type" to "object",
            "properties" to mapOf(
                "src" to mapOf("type" to "string"),
                "module" to mapOf("type" to "string","default" to "app")
            ),
            "required" to listOf("src")
        ))
    )
    override fun invoke(jsonArgs: String): ToolResponse {
        val src = "\"src\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonArgs)?.groupValues?.get(1) ?: return ToolResponse.error("src?")
        val module = "\"module\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonArgs)?.groupValues?.get(1) ?: "app"
        val base = File(project.basePath!!)
        val img = ImageIO.read(File(base, src)) ?: return ToolResponse.error("image?")
        val densities = mapOf(
            "mipmap-mdpi" to 48, "mipmap-hdpi" to 72, "mipmap-xhdpi" to 96,
            "mipmap-xxhdpi" to 144, "mipmap-xxxhdpi" to 192
        )
        densities.forEach { (folder, size) ->
            val outDir = File(base, "$module/src/main/res/$folder")
            outDir.mkdirs()
            val scaled = img.getScaledInstance(size, size, Image.SCALE_SMOOTH)
            val outFile = File(outDir, "ic_launcher.png")
            val buffered = java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val g = buffered.createGraphics()
            g.drawImage(scaled, 0, 0, null); g.dispose()
            ImageIO.write(buffered, "png", outFile)
        }
        return ToolResponse.ok("""{"ok":true}""", "Иконки созданы в $module/src/main/res")
    }
}
