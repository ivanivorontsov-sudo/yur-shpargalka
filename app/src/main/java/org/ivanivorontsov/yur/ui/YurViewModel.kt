package org.ivanivorontsov.yur.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ivanivorontsov.yur.data.LegalDoc
import org.ivanivorontsov.yur.data.LegalItem
import org.ivanivorontsov.yur.data.LegalRepository
import org.ivanivorontsov.yur.update.UpdateChecker
import org.ivanivorontsov.yur.update.UpdateInfo

data class UiState(
    val doc: LegalDoc = LegalDoc(),
    val query: String = "",
    val sectionId: String? = null,
    val favoritesOnly: Boolean = false,
    val favorites: Set<String> = emptySet(),
    val selected: LegalItem? = null,
    val loading: Boolean = false,
    val message: String? = null,
    val dark: Boolean = true,
    val update: UpdateInfo? = null,
    val updateBusy: Boolean = false
)

class YurViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = LegalRepository(app)
    private val updater = UpdateChecker(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val doc = repo.loadCachedOrBundled()
        _state.value = UiState(doc = doc, favorites = repo.favoriteIds())
        refresh(silent = true)
        checkUpdate()
    }

    fun onQuery(q: String) = _state.update { it.copy(query = q) }
    fun selectSection(id: String?) = _state.update { it.copy(sectionId = id, favoritesOnly = false) }
    fun showFavorites() = _state.update { it.copy(favoritesOnly = true, sectionId = null) }
    fun open(item: LegalItem) = _state.update { it.copy(selected = item) }
    fun closeDetail() = _state.update { it.copy(selected = null) }
    fun toggleDark() = _state.update { it.copy(dark = !it.dark) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    fun toggleFav(id: String) {
        val next = repo.toggleFavorite(id)
        _state.update { it.copy(favorites = next) }
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _state.update { it.copy(loading = true, message = null) }
            val result = repo.refreshFromNetwork()
            result.onSuccess { (doc, change) ->
                _state.update {
                    it.copy(doc = doc, loading = false, message = if (silent) null else change)
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        loading = false,
                        message = if (silent) null else "Нет сети, показан офлайн-кэш. ${e.message ?: ""}"
                    )
                }
            }
        }
    }

    fun checkUpdate() {
        viewModelScope.launch {
            updater.check().onSuccess { info ->
                _state.update { it.copy(update = info) }
            }
        }
    }

    fun installUpdate() {
        val info = _state.value.update ?: return
        viewModelScope.launch {
            _state.update { it.copy(updateBusy = true, message = "Скачиваю обновление…") }
            updater.downloadApk(info.apkUrl).onSuccess { file ->
                _state.update { it.copy(updateBusy = false, message = "Установите обновление ${info.tag}") }
                updater.installApk(file)
            }.onFailure { e ->
                _state.update { it.copy(updateBusy = false, message = "Не удалось скачать: ${e.message}") }
            }
        }
    }

    fun visibleItems(): List<LegalItem> {
        val s = _state.value
        val q = s.query.trim().lowercase()
        return s.doc.items.asSequence()
            .filter { item ->
                when {
                    s.favoritesOnly -> item.id in s.favorites
                    s.sectionId != null -> item.section == s.sectionId
                    else -> true
                }
            }
            .filter { item ->
                if (q.isEmpty()) true
                else listOf(item.title, item.rule, item.penalty, item.todo, item.doubt.orEmpty())
                    .any { it.lowercase().contains(q) }
            }
            .sortedWith(
                compareBy<LegalItem> { severityRank(it.severity) }
                    .thenBy { it.title }
            )
            .toList()
    }

    private fun severityRank(sev: String): Int = when (sev) {
        "criminal", "red" -> 0
        "fine_large", "orange" -> 1
        "fine_mid", "yellow" -> 2
        else -> 3
    }
}
