@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
package com.igorbarros.dosemed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HistoryScreen(vm: AppVm, meds: List<Medication>, logs: List<DoseLog>) {
    var days by remember { mutableIntStateOf(30) }
    var dialog by remember { mutableStateOf<DoseItem?>(null) }
    val now = System.currentTimeMillis()
    val to = today(); val from = to.minusDays(days - 1L)
    val items = remember(meds, logs, days) { doseItems(meds, logs, from, to, now) }
    val past = items.filter { it.state != DoseState.PENDING }
    val st = statsOf(items)
    val groups = past.asReversed().groupBy { it.at.toLocal().toLocalDate() }

    Scaffold(topBar = { TopAppBar(title = { Text("Histórico", fontWeight = FontWeight.Bold) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { ChipRow(listOf(7 to "7 dias", 30 to "30 dias", 90 to "90 dias"), days) { days = it } }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Text("Adesão no período", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (st.pct < 0) "—" else "${st.pct}%", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("${st.taken} tomadas", color = stateColor(DoseState.TAKEN))
                        Text("${st.skipped} puladas", color = stateColor(DoseState.SKIPPED))
                        Text("${st.missed} perdidas", color = stateColor(DoseState.MISSED))
                    }
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        var d = from
                        while (!d.isAfter(to)) {
                            val di = items.filter { it.at.toLocal().toLocalDate() == d && it.state != DoseState.PENDING && it.state != DoseState.LATE }
                            val s = statsOf(di)
                            val c = when { s.pct < 0 -> MaterialTheme.colorScheme.surfaceVariant; s.pct >= 100 -> Color(0xFF16A34A); s.pct >= 50 -> Color(0xFFF59E0B); else -> Color(0xFFDC2626) }
                            Box(Modifier.size(if (days > 30) 14.dp else 22.dp).clip(RoundedCornerShape(4.dp)).background(c))
                            d = d.plusDays(1)
                        }
                    }
                }
            }
            if (meds.any { it.type != "ASNEEDED" }) {
                item { SectionTitle("Por remédio") }
                for (m in meds.filter { it.type != "ASNEEDED" }) item(key = "m${m.id}") {
                    val s = statsOf(items.filter { it.med.id == m.id })
                    AppCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Dot(m.color, 12); Spacer(Modifier.width(10.dp))
                            Text(m.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text(if (s.pct < 0) "—" else "${s.pct}%", fontWeight = FontWeight.Bold)
                        }
                        if (s.pct >= 0) LinearProgressIndicator(progress = { s.pct / 100f }, Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
            }
            if (past.isEmpty()) item { Text("Sem registros no período.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp)) }
            for ((day, list) in groups) {
                item(key = "h$day") { SectionTitle(day.format(fmtDay).replaceFirstChar { it.uppercase() }) }
                for (it in list) item(key = "i${it.med.id}-${it.at}") {
                    AppCard(Modifier.fillMaxWidth(), onClick = { dialog = it }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(it.at.toLocal().format(fmtTime), Modifier.width(54.dp), fontWeight = FontWeight.Bold)
                            Dot(it.med.color, 10); Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(it.med.name, fontWeight = FontWeight.Medium)
                                if (!it.log?.note.isNullOrBlank()) Text(it.log!!.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(it.state.label, color = stateColor(it.state), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
    dialog?.let { DoseDialog(it, vm) { dialog = null } }
}

@Composable
fun ApptScreen(vm: AppVm, appts: List<Appointment>, onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    val now = System.currentTimeMillis()
    val upcoming = appts.filter { !it.done && it.dateTime >= now - 3_600_000L }.sortedBy { it.dateTime }
    val past = appts.filter { it !in upcoming }.sortedByDescending { it.dateTime }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Consultas", fontWeight = FontWeight.Bold) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Consulta") }) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (appts.isEmpty()) item { Text("Nenhuma consulta agendada.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (upcoming.isNotEmpty()) item { SectionTitle("Próximas") }
            for (a in upcoming) item(key = "u${a.id}") { ApptCard(vm, a, onEdit) }
            if (past.isNotEmpty()) item { SectionTitle("Anteriores") }
            for (a in past) item(key = "p${a.id}") { ApptCard(vm, a, onEdit) }
        }
    }
}

@Composable
private fun ApptCard(vm: AppVm, a: Appointment, onEdit: (Long) -> Unit) {
    val t = a.dateTime.toLocal()
    AppCard(Modifier.fillMaxWidth(), onClick = { onEdit(a.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(a.title, fontWeight = FontWeight.SemiBold)
                Text("${t.format(fmtDate)} às ${t.format(fmtTime)}", color = MaterialTheme.colorScheme.primary)
                val sub = listOf(a.doctor, a.location).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Checkbox(a.done, { vm.saveAppt(a.copy(done = it)) })
        }
    }
}

@Composable
fun ApptEditScreen(vm: AppVm, profileId: Long, existing: Appointment?, onClose: () -> Unit) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var doctor by remember { mutableStateOf(existing?.doctor ?: "") }
    var place by remember { mutableStateOf(existing?.location ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var date by remember { mutableStateOf(existing?.dateTime?.toLocal()?.toLocalDate() ?: today().plusDays(1)) }
    var time by remember { mutableStateOf(existing?.dateTime?.toLocal()?.toLocalTime()?.withSecond(0)?.withNano(0) ?: java.time.LocalTime.of(9, 0)) }
    var remind by remember { mutableIntStateOf(existing?.remindMinutes ?: 60) }
    var dDlg by remember { mutableStateOf(false) }; var tDlg by remember { mutableStateOf(false) }; var del by remember { mutableStateOf(false) }
    SubScaffold(if (existing == null) "Nova consulta" else "Editar consulta", onClose, actions = {
        if (existing != null) IconButton({ del = true }) { Icon(Icons.Default.Delete, "Apagar") }
        TextButton({
            if (title.isNotBlank()) {
                vm.saveAppt(Appointment(existing?.id ?: 0, existing?.profileId ?: profileId, title.trim(), doctor.trim(), place.trim(), millisOf(date, time), notes.trim(), remind, existing?.done ?: false))
                onClose()
            }
        }, enabled = title.isNotBlank()) { Text("Salvar", fontWeight = FontWeight.Bold) }
    }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Título (ex.: Cardiologista)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(doctor, { doctor = it }, label = { Text("Médico") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(place, { place = it }, label = { Text("Local") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip({ dDlg = true }, { Text(date.format(fmtDate)) }, leadingIcon = { Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp)) })
                AssistChip({ tDlg = true }, { Text(time.format(fmtTime)) }, leadingIcon = { Icon(Icons.Default.Schedule, null, Modifier.size(18.dp)) })
            }
            SectionTitle("Avisar antes")
            ChipRow(listOf(0 to "Sem aviso", 30 to "30 min", 60 to "1 hora", 1440 to "1 dia"), remind) { remind = it }
            OutlinedTextField(notes, { notes = it }, label = { Text("Notas (perguntas para o médico...)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        }
    }
    if (dDlg) DatePickerDlg(date, { dDlg = false }) { date = it; dDlg = false }
    if (tDlg) TimePickerDialog(time, { tDlg = false }) { time = it; tDlg = false }
    if (del && existing != null) ConfirmDialog("Apagar esta consulta?", onDismiss = { del = false }) { vm.deleteAppt(existing); onClose() }
}
