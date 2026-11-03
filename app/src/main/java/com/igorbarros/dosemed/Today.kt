@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
package com.igorbarros.dosemed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate

fun stateColor(s: DoseState): Color = when (s) {
    DoseState.TAKEN -> Color(0xFF16A34A)
    DoseState.SKIPPED -> Color(0xFF64748B)
    DoseState.MISSED -> Color(0xFFDC2626)
    DoseState.LATE -> Color(0xFFF59E0B)
    DoseState.PENDING -> Color(0xFF3B82F6)
}

@Composable
fun ProgressRing(done: Int, total: Int, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val col = MaterialTheme.colorScheme.primary
    Box(modifier.size(76.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 9.dp.toPx()
            drawArc(track, 0f, 360f, false, topLeft = Offset(sw / 2, sw / 2), size = Size(size.width - sw, size.height - sw), style = Stroke(sw))
            if (total > 0) drawArc(col, -90f, 360f * done / total, false, topLeft = Offset(sw / 2, sw / 2), size = Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
        }
        Text("$done/$total", fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun DoseDialog(item: DoseItem, vm: AppVm, onDismiss: () -> Unit) {
    var note by remember { mutableStateOf(item.log?.note ?: "") }
    val t = item.at.toLocal()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text(item.med.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${t.format(fmtDate)} às ${t.format(fmtTime)}" + if (item.med.dose.isNotBlank()) " · ${item.med.dose}" else "")
                Text(item.state.label, color = stateColor(item.state), fontWeight = FontWeight.SemiBold)
                OutlinedTextField(note, { note = it }, label = { Text("Nota (opcional)") }, modifier = Modifier.fillMaxWidth())
                Button({ vm.mark(item.med.id, item.at, "TAKEN", note); onDismiss() }, Modifier.fillMaxWidth()) { Text("Tomei") }
                FilledTonalButton({ vm.mark(item.med.id, item.at, "SKIPPED", note); onDismiss() }, Modifier.fillMaxWidth()) { Text("Pular dose") }
                if (item.log != null) TextButton({ vm.undo(item.med.id, item.at); onDismiss() }, Modifier.fillMaxWidth()) { Text("Desfazer registro") }
            }
        },
    )
}

@Composable
fun DoseRow(item: DoseItem, onClick: () -> Unit, onTake: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.at.toLocal().format(fmtTime), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.width(58.dp))
            Dot(item.med.color, 12)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.med.name, fontWeight = FontWeight.SemiBold)
                val sub = listOf(item.med.dose, item.med.form).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.state == DoseState.PENDING || item.state == DoseState.LATE) {
                FilledTonalButton(onTake, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Tomei") }
            } else {
                Text(item.state.label, color = stateColor(item.state), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun TodayScreen(
    vm: AppVm, profile: Profile?, profiles: List<Profile>, meds: List<Medication>, logs: List<DoseLog>, appts: List<Appointment>,
    setupNeeded: Boolean, onSetup: () -> Unit, onAddMed: () -> Unit, onGoAppts: () -> Unit,
) {
    var day by remember { mutableStateOf(today()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    var dialog by remember { mutableStateOf<DoseItem?>(null) }
    var menu by remember { mutableStateOf(false) }

    val doses = remember(meds, logs, day, now / 60000) { doseItems(meds, logs, day, day, now) }
    val done = doses.count { it.state == DoseState.TAKEN || it.state == DoseState.SKIPPED }
    val asNeeded = meds.filter { it.active && it.type == "ASNEEDED" }
    val low = meds.filter { it.stock != null && it.stock <= it.lowStockAt }
    val nextAppt = appts.filter { !it.done && it.dateTime > now }.minByOrNull { it.dateTime }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        Row(Modifier.let { it }, verticalAlignment = Alignment.CenterVertically) {
                            TextButton({ menu = true }) {
                                profile?.let { Dot(it.color) }
                                Spacer(Modifier.width(8.dp))
                                Text(profile?.name ?: "", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        }
                        DropdownMenu(menu, { menu = false }) {
                            profiles.forEach { p ->
                                DropdownMenuItem(text = { Text(p.name) }, leadingIcon = { Dot(p.color) }, onClick = { vm.selectProfile(p.id); menu = false })
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onAddMed, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Remédio") }) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (setupNeeded) item {
                AppCard(Modifier.fillMaxWidth(), onClick = onSetup) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Ative os alertas para não perder doses", fontWeight = FontWeight.SemiBold)
                            Text("Toque para permitir notificações e desativar a economia de bateria.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(done, doses.size)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (day == today()) "Hoje" else day.format(fmtDate), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text(day.format(fmtDay).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (doses.isEmpty()) "Nenhuma dose prevista" else if (done == doses.size) "Tudo em dia!" else "${doses.size - done} dose(s) restantes",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Column {
                            IconButton({ day = day.minusDays(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Dia anterior") }
                            IconButton({ day = day.plusDays(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo dia") }
                        }
                    }
                }
            }
            if (low.isNotEmpty()) item {
                AppCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Inventory2, null, tint = Color(0xFFF59E0B))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Estoque baixo", fontWeight = FontWeight.SemiBold)
                            Text(low.joinToString(", ") { "${it.name} (${it.stock})" }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (nextAppt != null) item {
                val t = nextAppt.dateTime.toLocal()
                AppCard(Modifier.fillMaxWidth(), onClick = onGoAppts) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EventNote, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Próxima consulta", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${nextAppt.title} · ${t.format(fmtDate)} ${t.format(fmtTime)}", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            if (doses.isEmpty() && asNeeded.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Medication, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(if (meds.isEmpty()) "Cadastre seu primeiro remédio" else "Sem doses neste dia", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(doses, key = { "${it.med.id}-${it.at}" }) { d ->
                DoseRow(d, onClick = { dialog = d }, onTake = { vm.mark(d.med.id, d.at, "TAKEN") })
            }
            if (asNeeded.isNotEmpty()) {
                item { SectionTitle("Se necessário") }
                items(asNeeded, key = { "sos-${it.id}" }) { m ->
                    AppCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Dot(m.color, 12); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.name, fontWeight = FontWeight.SemiBold)
                                val last = logs.filter { it.medId == m.id }.maxByOrNull { it.scheduledAt }
                                Text(if (last != null) "Última: " + last.scheduledAt.toLocal().let { it.format(fmtDate) + " " + it.format(fmtTime) } else m.dose,
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FilledTonalButton({ vm.takeNow(m) }) { Text("Registrar dose") }
                        }
                    }
                }
            }
        }
    }
    dialog?.let { DoseDialog(it, vm) { dialog = null } }
}
