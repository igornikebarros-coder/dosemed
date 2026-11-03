@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
package com.igorbarros.dosemed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun SubScaffold(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title, fontWeight = FontWeight.SemiBold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize(), content = content)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}

@Composable
fun Dot(color: Int, size: Int = 14) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color(color)))
}

@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PALETTE.forEach { c ->
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color(c)).clickable { onSelect(c) },
                contentAlignment = Alignment.Center,
            ) { if (c == selected) Icon(Icons.Default.Check, null, tint = Color.White) }
        }
    }
}

@Composable
fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) ->
            FilterChip(selected = v == selected, onClick = { onSelect(v) }, label = { Text(label) })
        }
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    if (onClick != null) Card(onClick = onClick, modifier = modifier, colors = colors, border = border) { Column(Modifier.padding(14.dp), content = content) }
    else Card(modifier = modifier, colors = colors, border = border) { Column(Modifier.padding(14.dp), content = content) }
}

@Composable
fun TimePickerDialog(initial: LocalTime, onDismiss: () -> Unit, onOk: (LocalTime) -> Unit) {
    val st = rememberTimePickerState(initial.hour, initial.minute, true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onOk(LocalTime.of(st.hour, st.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        text = { TimePicker(state = st) },
    )
}

@Composable
fun DatePickerDlg(initial: LocalDate, onDismiss: () -> Unit, onOk: (LocalDate) -> Unit) {
    val st = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                st.selectedDateMillis?.let { onOk(java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) { DatePicker(state = st) }
}

@Composable
fun ConfirmDialog(text: String, confirm: String = "Apagar", onDismiss: () -> Unit, onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onOk(); onDismiss() }) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
