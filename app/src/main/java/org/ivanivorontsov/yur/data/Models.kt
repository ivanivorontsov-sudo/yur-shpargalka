package org.ivanivorontsov.yur.data

import kotlinx.serialization.Serializable

@Serializable
data class SourceRef(val name: String, val url: String, val checked: String? = null)

@Serializable
data class LegalItem(
    val id: String,
    val section: String,
    val severity: String,
    val title: String,
    val rule: String,
    val penalty: String,
    val sources: List<SourceRef> = emptyList(),
    val todo: String = "",
    val doubt: String? = null,
    val checked_at: String? = null,
    val verification: String? = null,
    val verification_notes: String? = null
)

@Serializable
data class SectionMeta(
    val id: String,
    val title: String,
    val order: Int = 0,
    val blurb: String = ""
)

@Serializable
data class Contact(
    val id: String,
    val title: String,
    val short: String = "",
    val who: String = "",
    val phone: String? = null,
    val tel: String? = null,
    val url: String = "",
    val verified_on: String = "",
    val checked: String = "",
    val pinned: Boolean = false
)

@Serializable
data class ChangelogEntry(val date: String, val text: String)

@Serializable
data class LegalDoc(
    val version: Int = 1,
    val updated: String = "",
    val disclaimer: String = "",
    val sections: List<SectionMeta> = emptyList(),
    val severity_labels: Map<String, String> = emptyMap(),
    val items: List<LegalItem> = emptyList(),
    val changelog: List<ChangelogEntry> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val contacts_note: String = ""
)
