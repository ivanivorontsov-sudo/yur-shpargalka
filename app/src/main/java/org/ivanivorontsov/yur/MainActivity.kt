package org.ivanivorontsov.yur

import android.os.Bundle
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    LaunchedEffect(state.message) {
        val m = state.message ?: return@LaunchedEffect
        snack.showSnackbar(m)
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
            HomeScreen(vm, Modifier.padding(pad))
        }
    }
    if (state.update != null && !state.updateBusy) {
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: YurViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val items = vm.visibleItems()
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQuery("") }) {
                        Icon(Icons.Default.Close, null)
                    }
                }
            },
            placeholder = { Text("Поиск по всем пунктам") },
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(10.dp))
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
        Spacer(Modifier.height(8.dp))
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
        if (state.update != null) {
            Spacer(Modifier.height(8.dp))
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
        Spacer(Modifier.height(8.dp))
        Text(
            "${items.size} пунктов",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
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
                "Проверено по источнику: ${item.checked_at}" + (if (item.verification != null) " · ${item.verification}" else ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(14.dp))
        Block("Правило", item.rule)
        Block("Наказание / риск", item.penalty)
        Block("Что делать", item.todo)
        if (!item.doubt.isNullOrBlank()) {
            Block("⚠ Спорно", item.doubt!!, warn = true)
        }
        if (item.sources.isNotEmpty()) {
            Text("Источники", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            item.sources.forEach { src ->
                Text(
                    text = "• ${src.name}",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (src.url.isNotBlank()) uriHandler.openUri(src.url)
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
