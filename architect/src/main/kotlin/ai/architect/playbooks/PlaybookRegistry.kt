package ai.architect.playbooks

object PlaybookRegistry {
    private val playbooks: Map<String, Playbook> = listOf(
        XmlToComposePlaybook(),
        // здесь будут другие: WearTilePlaybook(), KmpModuleAutogenPlaybook(), ...
    ).associateBy { it.id }

    fun all(): List<Playbook> = playbooks.values.sortedBy { it.title }
    fun byId(id: String): Playbook? = playbooks[id]
}
