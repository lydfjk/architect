package ai.architect.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.CredentialAttributesKt
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.credentialStore.Credentials
import com.intellij.openapi.project.Project

class SecretStore(private val project: Project) {
    private fun attrs(): CredentialAttributes {
        val service = CredentialAttributesKt.generateServiceName("Architect", "DEEPSEEK_API_KEY")
        return CredentialAttributes(service)
    }

    fun saveApiKey(key: String?) {
        val credentials = if (key.isNullOrBlank()) null else Credentials("api", key)
        PasswordSafe.instance.set(attrs(), credentials)
    }

    fun loadApiKey(): String? {
        // приоритет: PasswordSafe → env var
        val fromSafe = PasswordSafe.instance.get(attrs())?.getPasswordAsString()
        return fromSafe ?: System.getenv("DEEPSEEK_API_KEY")
    }
}
