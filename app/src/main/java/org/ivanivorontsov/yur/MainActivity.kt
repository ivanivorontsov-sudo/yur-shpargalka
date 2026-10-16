package org.ivanivorontsov.yur

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.ivanivorontsov.yur.data.Contact
import org.ivanivorontsov.yur.data.LegalItem
import org.ivanivorontsov.yur.ui.YurTheme
import org.ivanivorontsov.yur.ui.YurViewModel
import org.ivanivorontsov.yur.ui.severityColor
import org.ivanivorontsov.yur.ui.severityLabel

class MainActivity : ComponentActivity() {
    private val vm: YurViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            YurTheme(dark = state.dark) {
                AppRoot(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: YurViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    LaunchedEffect(state.message) {
        val m = state.message ?: return@LaunchedEffect
        snack.showSnackbar(m, withDismissAction = true, duration = androidx.compose.material3.SnackbarDuration.Long)
        vm.clearMessage()
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Юр-шпаргалка", fontWeight = FontWeight.Bold)
                        Text(
                            "обновлено: ${state.doc.updated.ifBlank { "—" }}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (state.update != null) {
                        IconButton(onClick = { vm.installUpdate() }, enabled = !state.updateBusy) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = "Обновить приложение")
                        }
                    }
                    IconButton(onClick = { vm.refresh() }) {
                        if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = "Обновить данные")
                    }
                    IconButton(onClick = { vm.toggleDark() }) {
                        Icon(Icons.Default.DarkMode, contentDescription = "Тема")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { pad ->
        BackHandler(enabled = state.selected != null) { vm.closeDetail() }
        if (state.selected != null) {
            DetailScreen(
                item = state.selected!!,
                fav = state.selected!!.id in state.favorites,
                labels = state.doc.severity_labels,
                onBack = { vm.closeDetail() },
                onFav = { vm.toggleFav(state.selected!!.id) },
                modifier = Modifier.padding(pad)
            )
        } else {
            HomeScreen(vm, listState, Modifier.padding(pad))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: YurViewModel, listState: LazyListState, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val items = vm.visibleItems()
    val pinned = state.doc.contacts.filter { it.pinned }
    val helpOpen = state.sectionId == "help" && !state.favoritesOnly
    val imeVisible = WindowInsets.isImeVisible
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (pinned.isNotEmpty() && !imeVisible && state.query.isEmpty()) {
            PinnedContacts(pinned, onAll = { vm.selectSection("help") })
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Очистить")
                    }
                }
            },
            placeholder = { Text("Поиск по всем пунктам") },
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = state.favoritesOnly,
                    onClick = { vm.showFavorites() },
                    label = { Text("★ Избранное") }
                )
            }
            item {
                FilterChip(
                    selected = state.sectionId == null && !state.favoritesOnly,
                    onClick = { vm.selectSection(null) },
                    label = { Text("Все") }
                )
            }
            items(state.doc.sections.sortedBy { it.order }) { sec ->
                FilterChip(
                    selected = state.sectionId == sec.id && !state.favoritesOnly,
                    onClick = { vm.selectSection(sec.id) },
                    label = { Text(sec.title) }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "disclaimer") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        state.doc.disclaimer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        }
        if (state.update != null) {
            item(key = "update") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth().clickable { vm.installUpdate() }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Доступна версия приложения ${state.update!!.tag}. Нажмите, чтобы установить.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        if (helpOpen) {
            item(key = "helpnote") {
                Text(
                    state.doc.contacts_note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
            items(state.doc.contacts.sortedByDescending { it.pinned }, key = { "c_" + it.id }) { c ->
                ContactCard(c)
            }
        }
        item(key = "count") {
            Text(
                pluralItems(items.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(items, key = { it.id }) { item ->
            ItemCard(
                item = item,
                labels = state.doc.severity_labels,
                fav = item.id in state.favorites,
                onOpen = { vm.open(item) },
                onFav = { vm.toggleFav(item.id) }
            )
        }
    }
}
}

fun dial(context: android.content.Context, tel: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun PinnedContacts(list: List<Contact>, onAll: () -> Unit) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Официальная бесплатная помощь", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onAll) { Text("Все контакты →", fontSize = 13.sp) }
            }
            list.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { c ->
                        Row(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { c.tel?.let { dial(context, it) } }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Позвонить", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text(c.short.ifBlank { c.title }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(c.phone.orEmpty(), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun ContactCard(c: Contact) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(c.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp)
            if (c.who.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(c.who, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
            }
            if (!c.tel.isNullOrBlank()) {
                TextButton(onClick = { dial(context, c.tel) }) {
                    Icon(Icons.Default.Call, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(c.phone ?: c.tel, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (c.url.isNotBlank()) {
                TextButton(onClick = { runCatching { uri.openUri(c.url) } }) {
                    Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Открыть сайт", fontSize = 15.sp)
                }
            }
            if (c.checked.isNotBlank()) {
                Text(
                    "Проверено ${c.checked} на официальной странице",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ItemCard(
    item: LegalItem,
    labels: Map<String, String>,
    fav: Boolean,
    onOpen: () -> Unit,
    onFav: () -> Unit
) {
    val color = severityColor(item.severity)
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(color.copy(alpha = 0.18f))
                        .border(1.dp, color, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        severityLabel(item.severity, labels),
                        color = color,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onFav, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Избранное",
                        tint = if (fav) severityColor("criminal") else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                item.rule,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )
            if (!item.doubt.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, Modifier.size(16.dp), LargeOrangeHint())
                    Spacer(Modifier.width(4.dp))
                    Text(item.doubt!!, color = LargeOrangeHint(), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun LargeOrangeHint() = severityColor("orange")

@Composable
fun DetailScreen(
    item: LegalItem,
    fav: Boolean,
    labels: Map<String, String>,
    onBack: () -> Unit,
    onFav: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val color = severityColor(item.severity)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onFav) {
                Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
            }
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.18f))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(severityLabel(item.severity, labels), color = color, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Text(item.title, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp)
        if (!item.checked_at.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Проверено: ${item.checked_at}" + (item.verification?.let { " · " + verificationLabel(it) } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(14.dp))
        Block("Правило", item.rule)
        Block("Наказание / риск", item.penalty)
        Block("Что делать", item.todo)
        if (!item.verification_notes.isNullOrBlank()) {
            Block("Сверка", item.verification_notes!!)
        }
        if (!item.doubt.isNullOrBlank()) {
            Block("⚠ Спорно", item.doubt!!, warn = true)
        }
        if (item.sources.isNotEmpty()) {
            Text("Источники", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            item.sources.forEach { src ->
                Text(
                    text = "• ${src.name}" + (src.checked?.let { "  ✓ $it" } ?: ""),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (src.url.isNotBlank()) runCatching { uriHandler.openUri(src.url) }
                        }
                        .padding(vertical = 6.dp),
                    fontSize = 15.sp
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun Block(title: String, body: String, warn: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (warn) severityColor("orange").copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, lineHeight = 22.sp)
        }
    }
}

fun verificationLabel(v: String): String = when (v) {
    "primary" -> "по тексту закона"
    "partial" -> "частично"
    "policy" -> "правило ассистента"
    "unverified" -> "НЕ ПРОВЕРЕНО"
    else -> v
}

fun pluralItems(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    val w = when {
        m10 == 1 && m100 != 11 -> "пункт"
        m10 in 2..4 && m100 !in 12..14 -> "пункта"
        else -> "пунктов"
    }
    return "$n $w"
}
