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
                title = { Text(titleFor(state)) },
                navigationIcon = {
                    if (state.isLoggedIn) ChildMenu(
                        state = state,
                        onSelect = vm::select,
                        onSideBySide = vm::setSideBySide,
                        onAdd = { showAddChild = true },
                        onRemove = { confirmRemove = true },
                        onSettings = { showSettings = true }
                    )
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
                !state.isLoggedIn -> LoginScreen(isAdding = false) { login, pw, onError ->
                    vm.addAccount(login, pw, onError)
                }
                state.showsSideBySide -> SideBySideScreen(state = state, onWeek = vm::setWeek, onSelectChild = { id ->
                    vm.setSideBySide(false); vm.select(id)
                })
                else -> WeekScreen(state = state, onWeek = vm::setWeek)
            }
        }
    }

    if (showAddChild) ModalBottomSheet(onDismissRequest = { showAddChild = false }) {
        LoginScreen(isAdding = true) { login, pw, onError ->
            vm.addAccount(login, pw) { onError(it) }
            showAddChild = false
        }
    }

    if (showSettings) ModalBottomSheet(onDismissRequest = { showSettings = false }) {
        SettingsScreen(current = vm.settings.language, onLanguage = vm::setLanguage)
    }

    if (confirmRemove && state.current != null) {
        val name = state.current!!.shortName
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = {
                Text(if (state.accounts.size > 1) stringResource(R.string.remove_child_title, name)
                     else stringResource(R.string.log_out_title))
            },
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
private fun titleFor(state: AppUiState): String {
    if (!state.isLoggedIn) return stringResource(R.string.app_name)
    if (state.showsSideBySide) return stringResource(R.string.all_children)
    return state.current?.shortName ?: stringResource(R.string.app_name)
}

@Composable
private fun ChildMenu(
    state: AppUiState,
    onSelect: (java.util.UUID) -> Unit,
    onSideBySide: (Boolean) -> Unit,
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
                    if (acct.id == state.selectedId && !state.showsSideBySide) Icon(Icons.Default.Check, null)
                    else Spacer(Modifier.size(24.dp))
                },
                onClick = { onSelect(acct.id); open = false }
            )
        }
        if (state.accounts.size > 1) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.side_by_side)) },
                leadingIcon = {
                    if (state.showsSideBySide) Icon(Icons.Default.Check, null)
                    else Icon(Icons.Default.ViewColumn, null)
                },
                onClick = { onSideBySide(true); open = false }
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.add_child)) },
            leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
            onClick = { onAdd(); open = false }
        )
        if (state.current != null && !state.showsSideBySide) {
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
                submitting = true; error = null
                onSubmit(login.trim(), password) { error = it; submitting = false }
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

// MARK: Single child

@Composable
private fun WeekScreen(state: AppUiState, onWeek: (Day) -> Unit) {
    val data = state.data
    val summary = data?.let { WeekSummary(state.week, it, it.timetable[state.week.string]) }
    val changes = state.changes
    val newGradeIds = changes?.newGradeIds.orEmpty()
    val newAnnIds = changes?.newAnnouncementIds.orEmpty()
    val highlighted = changes?.highlightedEventIds.orEmpty()

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Spacer(Modifier.height(8.dp)) }
        item { WeekNavigator(state.week, onWeek) }
        state.error?.let { err -> item { ErrorBanner(err) } }

        if (summary == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    if (state.loading) CircularProgressIndicator()
                    else Text(stringResource(R.string.no_data))
                }
            }
            return@LazyColumn
        }

        changes?.let { item { ChangesSummary(it) } }
        item { OverviewCard(summary) }
        if (data!!.warnings.isNotEmpty()) item { WarningsCard(data.warnings) }

        if (summary.grades.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.new_grades)) }
            items(summary.grades, key = { it.id }) { GradeRow(it, isNew = it.id in newGradeIds) }
        }
        if (summary.cancelled.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.cancelled_lessons)) }
            items(summary.cancelled, key = { it.id }) { CancelledRow(it) }
        }
        if (summary.addedEvents.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.added_this_week)) }
            items(summary.addedEvents, key = { it.id }) { EventRow(it, showDay = true, isNew = it.id in highlighted) }
        }

        summary.days.forEach { day ->
            item { SectionHeader(day.day.string, isToday = day.day == Day.today) }
            items(day.events, key = { it.id }) { EventRow(it, showDay = false, isNew = it.id in highlighted) }
            items(day.lessons, key = { it.id }) { LessonRow(it) }
            if (day.events.isEmpty() && day.lessons.isEmpty()) item { EmptyDay(summary.hasTimetable) }
        }

        if (summary.announcements.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.announcements)) }
            items(summary.announcements, key = { it.id }) { AnnouncementRow(it, isNew = it.id in newAnnIds) }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

// MARK: Side by side

@Composable
private fun SideBySideScreen(state: AppUiState, onWeek: (Day) -> Unit, onSelectChild: (java.util.UUID) -> Unit) {
    val accounts = state.accounts
    val summaries: Map<java.util.UUID, WeekSummary> = accounts.mapNotNull { a ->
        val data = state.datas[a.id] ?: return@mapNotNull null
        a.id to WeekSummary(state.week, data, data.timetable[state.week.string])
    }.toMap()

    Column(Modifier.fillMaxSize()) {
        // Pinned header row: lives outside the LazyColumn so it is always visible while scrolling.
        SideColumns(accounts.map { it.id }) { id ->
            val acct = accounts.first { it.id == id }
            TextButton(
                onClick = { onSelectChild(id) },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(acct.shortName, style = MaterialTheme.typography.titleSmall,
                         fontWeight = FontWeight.SemiBold)
                    state.datas[id]?.fetchedAt?.let { ts ->
                        Text("Zaktualizowano ${formatTimestamp(ts)}",
                             style = MaterialTheme.typography.labelSmall,
                             color = MaterialTheme.colorScheme.outline)
                    }
                    if (id in state.loadingIds) Text(stringResource(R.string.refreshing),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }
        }
        HorizontalDivider()

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Spacer(Modifier.height(8.dp)) }
            item { WeekNavigator(state.week, onWeek) }

            item { SectionHeader(stringResource(R.string.this_week_heading)) }
        item {
            SideColumns(accounts.map { it.id }) { id ->
                val s = summaries[id]
                val ch = state.allChanges[id]
                ChildOverviewColumn(s, ch)
            }
        }

        val gradesByChild = accounts.associate { it.id to (summaries[it.id]?.grades ?: emptyList()) }
        if (gradesByChild.values.any { it.isNotEmpty() }) {
            item { SectionHeader(stringResource(R.string.new_grades)) }
            item {
                SideColumns(accounts.map { it.id }) { id ->
                    GradesColumn(gradesByChild[id].orEmpty(), state.allChanges[id]?.newGradeIds.orEmpty())
                }
            }
        }

        // Day-by-day grid.
        val visibleDays = daysUnion(summaries, state.week)
        visibleDays.forEach { day ->
            item { SectionHeader(day.string, isToday = day == Day.today) }
            item {
                SideColumns(accounts.map { it.id }) { id ->
                    val s = summaries[id]
                    val highlighted = state.allChanges[id]?.highlightedEventIds.orEmpty()
                    DayColumn(s, day, highlighted)
                }
            }
        }

        val annsByChild = accounts.associate { it.id to (summaries[it.id]?.announcements ?: emptyList()) }
        if (annsByChild.values.any { it.isNotEmpty() }) {
            item { SectionHeader(stringResource(R.string.announcements)) }
            item {
                SideColumns(accounts.map { it.id }) { id ->
                    AnnouncementsAccordion(
                        items = annsByChild[id].orEmpty(),
                        newIds = state.allChanges[id]?.newAnnouncementIds.orEmpty()
                    )
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
        }  // LazyColumn
    }  // Column
}

private fun formatTimestamp(d: java.util.Date): String {
    val cal = java.util.Calendar.getInstance()
    val today = java.util.Calendar.getInstance()
    cal.time = d
    val fmt = if (cal.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
                   cal.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)) {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale("pl", "PL"))
    } else {
        java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale("pl", "PL"))
    }
    return fmt.format(d)
}

private fun daysUnion(summaries: Map<java.util.UUID, WeekSummary>, week: Day): List<Day> {
    val busyDays = summaries.values.flatMap { s -> s.days.map { it.day } }.toSet()
    return (0..6).mapNotNull { offset ->
        val d = week.adding(offset)
        if (offset < 5 || d in busyDays) d else null
    }
}

@Composable
private fun SideColumns(ids: List<java.util.UUID>, content: @Composable (java.util.UUID) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ids.forEachIndexed { i, id ->
            if (i > 0) VerticalDivider(Modifier.height(80.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.TopStart) { content(id) }
        }
    }
}

@Composable
private fun ChildOverviewColumn(summary: WeekSummary?, changes: UpdateChanges?) {
    if (summary == null) {
        Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.outline)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        OverviewLine(summary.grades.size, "nowa ocena", "nowe oceny", "nowych ocen")
        OverviewLine(summary.eventCount, "sprawdzian", "sprawdziany", "sprawdzianów")
        if (summary.hasTimetable) {
            OverviewLine(summary.cancelled.size, "odwołana lekcja", "odwołane lekcje", "odwołanych lekcji")
        }
        OverviewLine(summary.announcements.size, "ogłoszenie", "ogłoszenia", "ogłoszeń")
        summary.luckyNumber?.let {
            Text("Numerek ${it.number}", style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        changes?.let {
            Text(if (it.count == 0) stringResource(R.string.no_changes) else "${it.count} zmian",
                 style = MaterialTheme.typography.labelSmall,
                 color = if (it.count == 0) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun OverviewLine(n: Int, one: String, few: String, many: String) {
    Text(pl(n, one, few, many), style = MaterialTheme.typography.bodySmall,
         color = if (n == 0) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun GradesColumn(grades: List<NewGrade>, newIds: Set<Int>) {
    if (grades.isEmpty()) {
        Text(stringResource(R.string.none), style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.outline)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        grades.forEach { g ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g.grade.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(g.subject ?: "Przedmiot", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                if (g.id in newIds) { Spacer(Modifier.width(4.dp)); NewBadge() }
            }
        }
    }
}

@Composable
private fun DayColumn(summary: WeekSummary?, day: Day, highlighted: Set<Int>) {
    val entry = summary?.days?.firstOrNull { it.day == day }
    if (entry == null) {
        Text(if (summary == null) stringResource(R.string.loading) else "—",
             style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val range = formatLessonRange(entry.lessons)
        if (range.isNotEmpty()) {
            Text(range, style = MaterialTheme.typography.labelSmall,
                 fontWeight = FontWeight.SemiBold,
                 color = MaterialTheme.colorScheme.outline)
        }
        entry.events.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = {}, label = { Text(item.event.category, fontSize = 10.sp) },
                           modifier = Modifier.height(20.dp))
                if (item.id in highlighted) { Spacer(Modifier.width(4.dp)); NewBadge() }
            }
            item.event.subject?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                     fontWeight = FontWeight.SemiBold)
            }
            if (item.event.topic.isNotEmpty()) {
                Text(item.event.topic, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.outline)
            }
        }
        entry.lessons.forEach { l ->
            val color = when (l.kind) {
                LessonKind.NORMAL -> MaterialTheme.colorScheme.onSurface
                LessonKind.CANCELLED, LessonKind.SHIFTED_SOURCE -> MaterialTheme.colorScheme.error
                else -> Color(0xFFD97706)
            }
            val isGone = l.kind == LessonKind.CANCELLED || l.kind == LessonKind.SHIFTED_SOURCE
            Row(verticalAlignment = Alignment.Top) {
                Text("${l.number}", style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.outline,
                     modifier = Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(l.subject ?: "Lekcja", style = MaterialTheme.typography.bodySmall,
                         color = color,
                         textDecoration = if (isGone) TextDecoration.LineThrough else null)
                    lessonNote(l)?.let { note ->
                        Text(note, style = MaterialTheme.typography.labelSmall,
                             fontWeight = FontWeight.SemiBold, color = color)
                    }
                    val meta = listOfNotNull(l.classroom, l.teacher).joinToString(" · ")
                    if (l.kind != LessonKind.NORMAL && meta.isNotEmpty()) {
                        Text(meta, style = MaterialTheme.typography.labelSmall,
                             color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
        if (entry.events.isEmpty() && entry.lessons.isEmpty()) {
            Text(if (summary.hasTimetable) "Brak lekcji" else "Ładowanie…",
                 style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

/// Day span (first lesson start – last lesson end), skipping cancelled / moved-away entries.
private fun formatLessonRange(lessons: List<Lesson>): String {
    val present = lessons.filter { it.kind != LessonKind.CANCELLED && it.kind != LessonKind.SHIFTED_SOURCE }
    val starts = present.mapNotNull { hhmmToMinutes(it.start) }
    val ends = present.mapNotNull { hhmmToMinutes(it.end) }
    val first = starts.minOrNull() ?: return ""
    val last = ends.maxOrNull() ?: return ""
    fun fmt(m: Int) = "%d:%02d".format(m / 60, m % 60)
    return "${fmt(first)}–${fmt(last)}"
}

private fun hhmmToMinutes(s: String?): Int? {
    if (s.isNullOrBlank() || s.length < 4) return null
    val parts = s.take(5).split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

/// Mirrors the Swift `Lesson.note` extension — describes why a lesson is modified.
private fun lessonNote(l: Lesson): String? = when (l.kind) {
    LessonKind.NORMAL -> null
    LessonKind.CANCELLED -> "Odwołana"
    LessonKind.CHANGE -> {
        val old = listOfNotNull(l.oldSubject, l.oldTeacher).joinToString(", ")
        if (old.isEmpty()) "Zastępstwo" else "Zastępstwo za $old"
    }
    LessonKind.SHIFTED_SOURCE -> buildString {
        append("Przeniesiona")
        l.otherDay?.let { append(" na ").append(it.string) }
        l.otherNumber?.let { append(", lekcja ").append(it) }
    }
    LessonKind.SHIFTED_TARGET -> buildString {
        append("Przeniesiona")
        l.otherDay?.let { append(" z ").append(it.string) }
        l.otherNumber?.let { append(", lekcja ").append(it) }
    }
}

@Composable
private fun AnnouncementsAccordion(items: List<Announcement>, newIds: Set<String>) {
    if (items.isEmpty()) {
        Text(stringResource(R.string.none), style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.outline)
        return
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)) {
            Text(pl(items.size, "ogłoszenie", "ogłoszenia", "ogłoszeń"), fontWeight = FontWeight.SemiBold)
            if (newIds.isNotEmpty()) { Spacer(Modifier.width(4.dp)); NewBadge() }
            Spacer(Modifier.weight(1f))
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                 modifier = Modifier.size(16.dp))
        }
        if (expanded) {
            items.forEach { ann ->
                AnnouncementAccordionRow(ann, isNew = ann.id in newIds)
            }
        }
    }
}

@Composable
private fun AnnouncementAccordionRow(a: Announcement, isNew: Boolean) {
    var open by rememberSaveable(a.id) { mutableStateOf(false) }
    Column(Modifier.padding(start = 4.dp)) {
        TextButton(onClick = { open = !open }, contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)) {
            Text(a.subject, style = MaterialTheme.typography.bodySmall,
                 fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (isNew) { Spacer(Modifier.width(4.dp)); NewBadge() }
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                 modifier = Modifier.size(14.dp))
        }
        val meta = listOfNotNull(a.author, a.startDay?.string).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(meta, style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 2.dp))
        }
        if (open) {
            Text(a.text, style = MaterialTheme.typography.bodySmall,
                 modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp))
        }
    }
}

// MARK: Shared rows

@Composable
private fun WeekNavigator(week: Day, onWeek: (Day) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { onWeek(week.adding(-7)) }) { Icon(Icons.Default.ChevronLeft, null) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${week.string} – ${week.adding(6).string}", fontWeight = FontWeight.SemiBold)
                if (week != Day.defaultTimetableWeek) {
                    TextButton(onClick = { onWeek(Day.defaultTimetableWeek) }) {
                        Text(stringResource(R.string.this_week))
                    }
                }
            }
            IconButton(onClick = { onWeek(week.adding(7)) }) { Icon(Icons.Default.ChevronRight, null) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, isToday: Boolean = false) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall,
         color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
         modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
}

@Composable
private fun ChangesSummary(changes: UpdateChanges) {
    if (changes.count == 0) {
        Text(stringResource(R.string.no_changes_since_update),
             style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.outline,
             modifier = Modifier.padding(horizontal = 8.dp))
        return
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (changes.newGradeIds.isNotEmpty())
                Text(pl(changes.newGradeIds.size, "nowa ocena", "nowe oceny", "nowych ocen"),
                     style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (changes.newEventIds.isNotEmpty() || changes.changedEventIds.isNotEmpty())
                Text("${changes.newEventIds.size + changes.changedEventIds.size} zmian w kalendarzu",
                     style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (changes.lessonChanges.isNotEmpty())
                Text("${changes.lessonChanges.size} zmian w planie",
                     style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (changes.newAnnouncementIds.isNotEmpty())
                Text(pl(changes.newAnnouncementIds.size, "nowe ogłoszenie", "nowe ogłoszenia", "nowych ogłoszeń"),
                     style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
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
private fun NewBadge() {
    Box(Modifier.clip(RoundedCornerShape(50)).padding(0.dp)) {
        Text("NEW", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
             modifier = Modifier.clip(RoundedCornerShape(50))
                 .padding(horizontal = 5.dp, vertical = 1.dp))
    }
}

@Composable
private fun GradeRow(item: NewGrade, isNew: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top) {
        Box(Modifier.size(width = 36.dp, height = 28.dp).clip(RoundedCornerShape(6.dp))
                .padding(1.dp), contentAlignment = Alignment.Center) {
            Text(item.grade.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.subject ?: "Przedmiot", fontWeight = FontWeight.SemiBold)
                if (isNew) { Spacer(Modifier.width(6.dp)); NewBadge() }
            }
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
             style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Text("${lesson.number}. ${lesson.subject ?: "Lekcja"}",
             textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.weight(1f))
        Text("${lesson.start}–${lesson.end}", style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun EventRow(item: EventItem, showDay: Boolean, isNew: Boolean) {
    val event = item.event
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = {}, label = { Text(event.category, fontSize = 11.sp) },
                           modifier = Modifier.height(24.dp))
                Spacer(Modifier.width(8.dp))
                event.subject?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                if (isNew) { Spacer(Modifier.width(6.dp)); NewBadge() }
                Spacer(Modifier.weight(1f))
                val whenText = buildString {
                    if (showDay) { append(event.day.string); append(" · ") }
                    item.start?.let { append(it); item.end?.let { e -> append("–"); append(e) } }
                }
                Text(whenText, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.outline)
            }
            if (event.topic.isNotEmpty()) Text(event.topic, style = MaterialTheme.typography.bodyMedium)
            val details = buildList {
                event.teacher?.let { add(it) }
                item.classroom?.let { add("sala $it") }
            }
            if (details.isNotEmpty()) Text(details.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun LessonRow(lesson: Lesson) {
    val isGone = lesson.kind == LessonKind.CANCELLED || lesson.kind == LessonKind.SHIFTED_SOURCE
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("${lesson.number}", Modifier.width(24.dp), fontWeight = FontWeight.Bold)
        Column(Modifier.weight(1f)) {
            Text(lesson.subject ?: "Lekcja",
                 textDecoration = if (isGone) TextDecoration.LineThrough else null,
                 color = if (isGone) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
            val details = listOfNotNull(lesson.classroom, lesson.teacher).joinToString(" · ")
            if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline)
        }
        Text("${lesson.start}–${lesson.end}", style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun AnnouncementRow(a: Announcement, isNew: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.subject, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (isNew) { Spacer(Modifier.width(6.dp)); NewBadge() }
                TextButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }
            val meta = listOfNotNull(a.author, a.startDay?.string).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline)
            if (expanded) Text(a.text, style = MaterialTheme.typography.bodyMedium,
                               modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun EmptyDay(hasTimetable: Boolean) {
    Text(if (hasTimetable) "Brak lekcji" else "Ładowanie…",
         style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
         modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
}

// MARK: Settings

@Composable
private fun SettingsScreen(current: AppLanguage, onLanguage: (AppLanguage) -> Unit) {
    var selected by remember { mutableStateOf(current) }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
        AppLanguage.entries.forEach { lang ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = lang == selected,
                            onClick = { selected = lang; onLanguage(lang) })
                Text(lang.label, Modifier.padding(start = 8.dp))
            }
        }
        Text(stringResource(R.string.settings_language_note),
             style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.settings_footer), style = MaterialTheme.typography.bodySmall)
    }
}
