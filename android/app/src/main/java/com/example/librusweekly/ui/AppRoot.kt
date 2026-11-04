package com.example.librusweekly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.librusweekly.R

private sealed interface Screen {
    data object Login : Screen
    data object Week : Screen
    data object Settings : Screen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    var screen: Screen by rememberSaveable { mutableStateOf(Screen.Login as Screen) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (screen != Screen.Settings) {
                        IconButton(onClick = { screen = Screen.Settings }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.settings))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (screen) {
                is Screen.Login -> LoginScreen(onLoggedIn = { screen = Screen.Week })
                is Screen.Week -> WeekScreen(onLogout = { screen = Screen.Login })
                is Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Week })
            }
        }
    }
}

@Composable
private fun LoginScreen(onLoggedIn: () -> Unit) {
    var login by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.login_headline),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = login,
            onValueChange = { login = it },
            label = { Text(stringResource(R.string.synergia_login)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { if (login.isNotBlank() && password.isNotBlank()) onLoggedIn() },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.log_in)) }
        Text(
            text = stringResource(R.string.login_footer),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        // TODO: wire up a real Librus/Synergia session. Until then, tapping Log in just reveals the placeholder.
        Text(
            text = stringResource(R.string.todo_login),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun WeekScreen(onLogout: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(stringResource(R.string.this_week), style = MaterialTheme.typography.titleLarge)
        // TODO: port the Librus API client and render grades / timetable / announcements
        // the same way as the iOS screens. For now, this placeholder lets reviewers see the shell.
        Text(stringResource(R.string.week_placeholder), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onLogout) { Text(stringResource(R.string.log_out)) }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val options = listOf(
        "" to stringResource(R.string.lang_system),
        "en" to stringResource(R.string.lang_english),
        "pl" to stringResource(R.string.lang_polish)
    )
    var selected by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
        options.forEach { (code, label) ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = code == selected, onClick = { selected = code })
                Text(label, Modifier.padding(start = 8.dp))
            }
        }
        Text(stringResource(R.string.settings_footer), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onBack) { Text(stringResource(R.string.done)) }
    }
}
