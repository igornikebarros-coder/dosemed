@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
package com.igorbarros.dosemed

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun MedAvatar(m: Medication, size: Int = 44) {
    if (m.photoPath != null) {
        AsyncImage(File(m.photoPath), null, Modifier.size(size.dp).clip(CircleShape), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.size(size.dp).clip(CircleShape).background(Color(m.color).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Medication, null, tint = Color(m.color))
        }
    }
}

@Composable
fun MedsScreen(vm: AppVm, meds: List<Medication>, onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    var refill by remember { mutableStateOf<Medication?>(null) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Remédios", fontWeight = FontWeight.Bold) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Adicionar") }) },
    ) { pad ->
        if (meds.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhum remédio cadastrado ainda.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(meds, key = { it.id }) { m ->
                AppCard(Modifier.fillMaxWidth(), onClick = { onEdit(m.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MedAvatar(m)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name + if (!m.active) " (pausado)" else "", fontWeight = FontWeight.SemiBold)
                            Text(listOf(m.dose, m.form).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            Text(scheduleSummary(m), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (m.stock != null) {
                        val low = m.stock <= m.lowStockAt
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Inventory2, null, Modifier.size(18.dp), tint = if (low) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Text("Estoque: ${m.stock}" + if (low) " — baixo" else "", color = if (low) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            TextButton({ refill = m }) { Text("Repor") }
                        }
                    }
                }
            }
        }
    }
    refill?.let { m ->
        var qty by remember { mutableStateOf("30") }
        AlertDialog(
            onDismissRequest = { refill = null },
            title = { Text("Repor ${m.name}") },
            text = { OutlinedTextField(qty, { qty = it.filter(Char::isDigit) }, label = { Text("Quantidade comprada") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)) },
            confirmButton = { TextButton({ vm.refill(m, qty.toIntOrNull() ?: 0); refill = null }) { Text("Adicionar") } },
            dismissButton = { TextButton({ refill = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun MedEditScreen(vm: AppVm, profileId: Long, existing: Medication?, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var dose by remember { mutableStateOf(existing?.dose ?: "") }
    var form by remember { mutableStateOf(existing?.form ?: "Comprimido") }
    var color by remember { mutableIntStateOf(existing?.color ?: PALETTE[0]) }
    var photo by remember { mutableStateOf(existing?.photoPath) }
    var type by remember { mutableStateOf(existing?.type ?: "DAILY") }
    var times by remember { mutableStateOf(existing?.timeList() ?: listOf(LocalTime.of(8, 0))) }
    var interval by remember { mutableStateOf((existing?.intervalHours ?: 8).toString()) }
    var weekdays by remember { mutableIntStateOf(existing?.weekdays ?: 127) }
    var start by remember { mutableStateOf(existing?.let { LocalDate.ofEpochDay(it.startDate) } ?: today()) }
    var continuous by remember { mutableStateOf(existing?.endDate == null) }
    var days by remember { mutableStateOf(existing?.let { e -> e.endDate?.let { (it - e.startDate + 1).toString() } } ?: "7") }
    var control by remember { mutableStateOf(existing?.stock != null) }
    var stock by remember { mutableStateOf((existing?.stock ?: 30).toString()) }
    var perDose by remember { mutableStateOf((existing?.unitsPerDose ?: 1).toString()) }
    var lowAt by remember { mutableStateOf((existing?.lowStockAt ?: 5).toString()) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    var timeDlg by remember { mutableStateOf<Int?>(null) }   // índice (-1 = novo)
    var dateDlg by remember { mutableStateOf(false) }
    var delDlg by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importPhoto(uri) { if (it != null) photo = it }
    }
    val num = KeyboardType.Number
    val kbNum = androidx.compose.foundation.text.KeyboardOptions(keyboardType = num)

    fun save() {
        val n = name.trim(); if (n.isEmpty()) return
        val d = days.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val m = Medication(
            id = existing?.id ?: 0, profileId = existing?.profileId ?: profileId, name = n, dose = dose.trim(), form = form, color = color,
            photoPath = photo, type = type, times = times.sorted().joinToString(",") { it.format(fmtTime) },
            intervalHours = interval.toIntOrNull()?.coerceIn(1, 72) ?: 8,
            weekdays = if (weekdays == 0) 127 else weekdays,
            startDate = start.toEpochDay(), endDate = if (continuous || type == "ASNEEDED") null else start.toEpochDay() + d - 1,
            stock = if (control) (stock.toIntOrNull() ?: 0) else null,
            unitsPerDose = perDose.toIntOrNull()?.coerceAtLeast(1) ?: 1, lowStockAt = lowAt.toIntOrNull() ?: 5,
            notes = notes.trim(), active = active,
        )
        vm.saveMed(m); onClose()
    }

    SubScaffold(if (existing == null) "Novo remédio" else "Editar remédio", onClose, actions = {
        if (existing != null) IconButton({ delDlg = true }) { Icon(Icons.Default.Delete, "Apagar") }
        TextButton({ save() }, enabled = name.isNotBlank()) { Text("Salvar", fontWeight = FontWeight.Bold) }
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentAlignment = Alignment.Center) {
                    if (photo != null) AsyncImage(File(photo!!), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Default.AddAPhoto, "Foto", Modifier.size(30.dp))
                }
                if (photo != null) TextButton({ photo = null }) { Text("Remover foto") }
            }
            OutlinedTextField(name, { name = it }, label = { Text("Nome do remédio") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(dose, { dose = it }, label = { Text("Dose (ex.: 500 mg, 1 comprimido)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SectionTitle("Forma")
            ChipRow(FORMS.map { it to it }, form) { form = it }
            SectionTitle("Cor")
            ColorPicker(color) { color = it }

            SectionTitle("Frequência")
            ChipRow(listOf("DAILY" to "Todos os dias", "WEEKDAYS" to "Dias da semana", "INTERVAL" to "A cada X horas", "ASNEEDED" to "Se necessário"), type) { type = it }
            if (type == "WEEKDAYS") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WEEK.forEachIndexed { i, d ->
                        FilterChip(selected = ((weekdays shr i) and 1) == 1, onClick = { weekdays = weekdays xor (1 shl i) }, label = { Text(d) })
                    }
                }
            }
            if (type == "INTERVAL") {
                OutlinedTextField(interval, { interval = it.filter(Char::isDigit) }, label = { Text("Intervalo (horas)") }, singleLine = true, keyboardOptions = kbNum, modifier = Modifier.fillMaxWidth())
            }
            if (type == "DAILY" || type == "WEEKDAYS") {
                SectionTitle("Horários")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    times.forEachIndexed { i, t ->
                        InputChip(selected = false, onClick = { timeDlg = i }, label = { Text(t.format(fmtTime)) },
                            trailingIcon = { if (times.size > 1) Icon(Icons.Default.Close, "Remover", Modifier.size(18.dp).clickable { times = times.filterIndexed { j, _ -> j != i } }) })
                    }
                    AssistChip(onClick = { timeDlg = -1 }, label = { Text("Adicionar horário") }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) })
                }
            }
            if (type == "INTERVAL") {
                AssistChip(onClick = { timeDlg = 0 }, label = { Text("Primeira dose às ${times.first().format(fmtTime)}") }, leadingIcon = { Icon(Icons.Default.Schedule, null, Modifier.size(18.dp)) })
            }
            if (type != "ASNEEDED") {
                SectionTitle("Duração do tratamento")
                AssistChip(onClick = { dateDlg = true }, label = { Text("Início: ${start.format(fmtDate)}") }, leadingIcon = { Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp)) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Uso contínuo", Modifier.weight(1f)); Switch(continuous, { continuous = it })
                }
                if (!continuous) OutlinedTextField(days, { days = it.filter(Char::isDigit) }, label = { Text("Dias de tratamento") }, singleLine = true, keyboardOptions = kbNum, modifier = Modifier.fillMaxWidth())
            }

            SectionTitle("Estoque")
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Controlar estoque", Modifier.weight(1f)); Switch(control, { control = it }) }
            if (control) {
                OutlinedTextField(stock, { stock = it.filter(Char::isDigit) }, label = { Text("Unidades em estoque") }, singleLine = true, keyboardOptions = kbNum, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(perDose, { perDose = it.filter(Char::isDigit) }, label = { Text("Unidades por dose") }, singleLine = true, keyboardOptions = kbNum, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(lowAt, { lowAt = it.filter(Char::isDigit) }, label = { Text("Avisar quando restar até") }, singleLine = true, keyboardOptions = kbNum, modifier = Modifier.fillMaxWidth())
            }
            SectionTitle("Notas")
            OutlinedTextField(notes, { notes = it }, label = { Text("Observações (ex.: tomar com comida)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            if (existing != null) Row(verticalAlignment = Alignment.CenterVertically) { Text("Lembretes ativos", Modifier.weight(1f)); Switch(active, { active = it }) }
            Spacer(Modifier.height(32.dp))
        }
    }
    timeDlg?.let { idx ->
        TimePickerDialog(if (idx >= 0) times[idx] else LocalTime.of(12, 0), { timeDlg = null }) { t ->
            times = if (idx >= 0) times.mapIndexed { j, x -> if (j == idx) t else x } else (times + t).distinct()
            timeDlg = null
        }
    }
    if (dateDlg) DatePickerDlg(start, { dateDlg = false }) { start = it; dateDlg = false }
    if (delDlg && existing != null) ConfirmDialog("Apagar ${existing.name} e todo o histórico dele?", onDismiss = { delDlg = false }) { vm.deleteMed(existing); onClose() }
}
