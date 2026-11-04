package com.example.librusweekly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.librusweekly.R
import com.example.librusweekly.librus.models.*
import com.example.librusweekly.state.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var showAddChild by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.current?.shortName ?: stringResource(R.string.app_name)) },
                navigationIcon = {
                    if (state.isLoggedIn) {
                        ChildMenu(
                            state = state,
                            onSelect = vm::select,
                            onAdd = { showAddChild = true },
                            onRemove = { confirmRemove = true },
                            onSettings = { showSettings = true }
                        )
                    }
                },
                actions = {
                    if (state.isLoggedIn) {
                        IconButton(onClick = { vm.refresh() }, enabled = !state.loading) {
                            if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                    } else {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !state.isLoggedIn -> LoginScreen(
                    isAdding = false,
                    onSubmit = { login, password, onError -> vm.addAccount(login, password, onError) }
                )
                else -> WeekScreen(state = state, onWeek = vm::setWeek)
            }
        }
    }

    if (showAddChild) {
        ModalBottomSheet(onDismissRequest = { showAddChild = false }) {
            LoginScreen(
                isAdding = true,
                onSubmit = { login, password, onError ->
                    vm.addAccount(login, password) { onError(it) }
                    showAddChild = false
                }
            )
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            SettingsScreen()
        }
    }

    if (confirmRemove && state.current != null) {
        val name = state.current!!.shortName
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(if (state.accounts.size > 1) stringResource(R.string.remove_child_title, name)
                           else stringResource(R.string.log_out_title)) },
            confirmButton = {
                TextButton(onClick = { vm.removeCurrent(); confirmRemove = false }) {
                    Text(if (state.accounts.size > 1) stringResource(R.string.remove)
                         else stringResource(R.string.log_out))
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun ChildMenu(
    state: AppUiState,
    onSelect: (java.util.UUID) -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onSettings: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(if (state.accounts.size > 1) Icons.Default.Group else Icons.Default.AccountCircle,
             contentDescription = stringResource(R.string.children))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        state.accounts.forEach { acct ->
            DropdownMenuItem(
                text = { Text(acct.shortName) },
                leadingIcon = {
                    if (acct.id == state.selectedId) Icon(Icons.Default.Check, null)
                    else Spacer(Modifier.size(24.dp))
                },
                onClick = { onSelect(acct.id); open = false }
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.add_child)) },
            leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
            onClick = { onAdd(); open = false }
        )
        if (state.current != null) {
            DropdownMenuItem(
                text = { Text(if (state.accounts.size > 1) stringResource(R.string.remove_child)
                              else stringResource(R.string.log_out)) },
                leadingIcon = { Icon(Icons.Default.PersonRemove, null) },
                onClick = { onRemove(); open = false }
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.settings)) },
            leadingIcon = { Icon(Icons.Default.Settings, null) },
            onClick = { onSettings(); open = false }
        )
    }
}

@Composable
private fun LoginScreen(isAdding: Boolean, onSubmit: (String, String, (String) -> Unit) -> Unit) {
    var login by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var submitting by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!isAdding) {
            Text(stringResource(R.string.login_headline),
                 style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.login_subtitle), style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(
            value = login, onValueChange = { login = it },
            label = { Text(stringResource(R.string.synergia_login)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text(stringResource(R.string.password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                if (login.isBlank() || password.isBlank() || submitting) return@Button
                submitting = true
                error = null
                onSubmit(login.trim(), password) {
                    error = it
                    submitting = false
                }
            },
            enabled = !submitting && login.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(if (isAdding) stringResource(R.string.add_child) else stringResource(R.string.log_in))
        }
        Text(stringResource(R.string.login_footer), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun WeekScreen(state: AppUiState, onWeek: (Day) -> Unit) {
    val summary = state.data?.let { WeekSummary(state.week, it, it.timetable[state.week.string]) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Spacer(Modifier.height(8.dp)) }
        item { WeekNavigator(state.week, onWeek) }

        state.error?.let { err ->
            item { ErrorBanner(err) }
        }

        if (summary == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    if (state.loading) CircularProgressIndicator()
                    else Text(stringResource(R.string.no_data))
                }
            }
            return@LazyColumn
        }

        item { OverviewCard(summary) }

        if (state.data!!.warnings.isNotEmpty()) {
            item { WarningsCard(state.data!!.warnings) }
        }

        if (summary.grades.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.new_grades)) }
            items(summary.grades, key = { it.id }) { GradeRow(it) }
        }

        if (summary.cancelled.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.cancelled_lessons)) }
            items(summary.cancelled, key = { it.id }) { CancelledRow(it) }
        }

        if (summary.addedEvents.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.added_this_week)) }
            items(summary.addedEvents, key = { it.id }) { EventRow(it, showDay = true) }
        }

        summary.days.forEach { day ->
            item { SectionHeader(day.day.string, isToday = day.day == Day.today) }
            items(day.events, key = { it.id }) { EventRow(it, showDay = false) }
            items(day.lessons, key = { it.id }) { LessonRow(it) }
            if (day.events.isEmpty() && day.lessons.isEmpty()) {
                item { EmptyDay(summary.hasTimetable) }
            }
        }

        if (summary.announcements.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.announcements)) }
            items(summary.announcements, key = { it.id }) { AnnouncementRow(it) }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun WeekNavigator(week: Day, onWeek: (Day) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { onWeek(week.adding(-7)) }) {
                Icon(Icons.Default.ChevronLeft, null)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${week.string} – ${week.adding(6).string}", fontWeight = FontWeight.SemiBold)
                if (week != Day.defaultTimetableWeek) {
                    TextButton(onClick = { onWeek(Day.defaultTimetableWeek) }) {
                        Text(stringResource(R.string.this_week))
                    }
                }
            }
            IconButton(onClick = { onWeek(week.adding(7)) }) {
                Icon(Icons.Default.ChevronRight, null)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, isToday: Boolean = false) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun OverviewCard(summary: WeekSummary) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(pl(summary.grades.size, "nowa ocena", "nowe oceny", "nowych ocen"))
            Text(pl(summary.eventCount, "sprawdzian", "sprawdziany", "sprawdzianów"))
            if (summary.hasTimetable) {
                Text(pl(summary.cancelled.size, "odwołana lekcja", "odwołane lekcje", "odwołanych lekcji"))
                Text(pl(summary.otherChangeCount, "inna zmiana", "inne zmiany", "innych zmian"))
            }
            Text(pl(summary.announcements.size, "ogłoszenie", "ogłoszenia", "ogłoszeń"))
            summary.luckyNumber?.let {
                Text("Szczęśliwy numerek: ${it.number}", color = MaterialTheme.colorScheme.primary,
                     fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun WarningsCard(warnings: List<String>) {
    Card(Modifier.fillMaxWidth(),
         colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
         shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.not_everything_loaded),
                 fontWeight = FontWeight.SemiBold, color = Color(0xFF7C4A03))
            warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF7C4A03)) }
        }
    }
}

@Composable
private fun ErrorBanner(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
         shape = RoundedCornerShape(12.dp)) {
        Text(text, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun GradeRow(item: NewGrade) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(width = 36.dp, height = 28.dp).clip(RoundedCornerShape(6.dp))
                .padding(1.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(item.grade.name, fontWeight = FontWeight.Bold,
                 color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(item.subject ?: "Przedmiot", fontWeight = FontWeight.SemiBold)
            val bits = buildList {
                if (item.grade.category.isNotEmpty()) add(item.grade.category)
                if (item.grade.weight > 0) add("waga ${item.grade.weight.toInt()}")
                if (item.grade.isImprovement) add("poprawa")
            }
            if (bits.isNotEmpty())
                Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.outline)
            item.grade.comment?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CancelledRow(lesson: Lesson) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(lesson.day.string, Modifier.width(80.dp),
             style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.outline)
        Text("${lesson.number}. ${lesson.subject ?: "Lekcja"}",
             textDecoration = TextDecoration.LineThrough,
             color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.weight(1f))
        Text("${lesson.start}–${lesson.end}",
             style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun EventRow(item: EventItem, showDay: Boolean) {
    val event = item.event
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = {},
                    label = { Text(event.category, fontSize = 11.sp) },
                    modifier = Modifier.height(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                event.subject?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.weight(1f))
                val when_ = buildString {
                    if (showDay) { append(event.day.string); append(" · ") }
                    item.start?.let {
                        append(it)
                        item.end?.let { e -> append("–"); append(e) }
                    }
                }
                Text(when_, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.outline)
            }
            if (event.topic.isNotEmpty()) Text(event.topic, style = MaterialTheme.typography.bodyMedium)
            val details = buildList {
                event.teacher?.let { add(it) }
                item.classroom?.let { add("sala $it") }
            }
            if (details.isNotEmpty())
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun LessonRow(lesson: Lesson) {
    val isGone = lesson.kind == LessonKind.CANCELLED || lesson.kind == LessonKind.SHIFTED_SOURCE
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("${lesson.number}", Modifier.width(24.dp),
             fontWeight = FontWeight.Bold)
        Column(Modifier.weight(1f)) {
            Text(lesson.subject ?: "Lekcja",
                 textDecoration = if (isGone) TextDecoration.LineThrough else null,
                 color = if (isGone) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
            val details = listOfNotNull(lesson.classroom, lesson.teacher).joinToString(" · ")
            if (details.isNotEmpty())
                Text(details, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.outline)
        }
        Text("${lesson.start}–${lesson.end}",
             style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun AnnouncementRow(a: Announcement) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(10.dp)) {
            Row {
                Text(a.subject, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }
            val meta = listOfNotNull(a.author, a.startDay?.string).joinToString(" · ")
            if (meta.isNotEmpty())
                Text(meta, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.outline)
            if (expanded) Text(a.text, style = MaterialTheme.typography.bodyMedium,
                               modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun EmptyDay(hasTimetable: Boolean) {
    Text(if (hasTimetable) "Brak lekcji" else "Ładowanie…",
         style = MaterialTheme.typography.bodySmall,
         color = MaterialTheme.colorScheme.outline,
         modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
}

@Composable
private fun SettingsScreen() {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_language_note),
             style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.outline)
        Text(stringResource(R.string.settings_footer), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
    }
}
