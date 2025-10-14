package ai.architect.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.project.Project

class SecretStore(private val project: Project) {
    private fun attr(name: String): CredentialAttributes =
        CredentialAttributes("Architect/${project.name.ifBlank { "Project" }}/$name")

    // DeepSeek
    fun saveApiKey(key: String?) {
        val credentials = if (key.isNullOrBlank()) null else Credentials("api", key)
        PasswordSafe.instance.set(attr("DEEPSEEK_API_KEY"), credentials)
    }
    fun loadApiKey(): String? =
        PasswordSafe.instance.get(attr("DEEPSEEK_API_KEY"))?.getPasswordAsString()
            ?: System.getenv("DEEPSEEK_API_KEY")

    // GitHub
    fun saveGithubToken(token: String?) {
        val credentials = if (token.isNullOrBlank()) null else Credentials("api", token)
        PasswordSafe.instance.set(attr("GITHUB_TOKEN"), credentials)
    }
    fun loadGithubToken(): String? =
        PasswordSafe.instance.get(attr("GITHUB_TOKEN"))?.getPasswordAsString()
            ?: System.getenv("GITHUB_TOKEN")
}
