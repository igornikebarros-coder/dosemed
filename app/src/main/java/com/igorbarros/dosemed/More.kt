@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
package com.igorbarros.dosemed

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

fun ignoringBattery(ctx: Context): Boolean =
    ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

fun canExact(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 31 || ctx.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()

fun askIgnoreBattery(ctx: Context) {
    val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(i) }.onFailure {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

@Composable
fun MoreScreen(onOpen: (String) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Mais", fontWeight = FontWeight.Bold) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("profiles", "Perfis da família", Icons.Default.Groups),
                Triple("notes", "Notas", Icons.Default.StickyNote2),
                Triple("report", "Relatório para o médico", Icons.Default.Description),
                Triple("settings", "Ajustes e alertas", Icons.Default.Settings),
            ).forEach { (k, label, icon) ->
                AppCard(Modifier.fillMaxWidth(), onClick = { onOpen(k) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp))
                        Text(label, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
            Text("Dosemed é um organizador pessoal e não substitui orientação médica.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
fun ProfilesScreen(vm: AppVm, profiles: List<Profile>, activeId: Long, onBack: () -> Unit) {
    var edit by remember { mutableStateOf<Profile?>(null) }
    var del by remember { mutableStateOf<Profile?>(null) }
    SubScaffold("Perfis da família", onBack, actions = {
        IconButton({ edit = Profile(0, "", PALETTE[(profiles.size) % PALETTE.size]) }) { Icon(Icons.Default.PersonAdd, "Novo perfil") }
    }) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(profiles, key = { it.id }) { p ->
                AppCard(Modifier.fillMaxWidth(), onClick = { vm.selectProfile(p.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(p.color, 20); Spacer(Modifier.width(12.dp))
                        Text(p.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        if (p.id == activeId) Icon(Icons.Default.CheckCircle, "Ativo", tint = MaterialTheme.colorScheme.primary)
                        IconButton({ edit = p }) { Icon(Icons.Default.Edit, "Editar") }
                        if (profiles.size > 1) IconButton({ del = p }) { Icon(Icons.Default.Delete, "Apagar") }
                    }
                }
            }
            item { Text("Toque em um perfil para ativá-lo. Cada perfil tem seus próprios remédios, consultas e notas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    edit?.let { p ->
        var name by remember { mutableStateOf(p.name) }; var color by remember { mutableIntStateOf(p.color) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(if (p.id == 0L) "Novo perfil" else "Editar perfil") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                ColorPicker(color) { color = it }
            } },
            confirmButton = { TextButton({ if (name.isNotBlank()) { vm.saveProfile(p.copy(name = name.trim(), color = color)); edit = null } }) { Text("Salvar") } },
            dismissButton = { TextButton({ edit = null }) { Text("Cancelar") } },
        )
    }
    del?.let { p -> ConfirmDialog("Apagar o perfil ${p.name} e todos os remédios, consultas e notas dele?", onDismiss = { del = null }) { vm.deleteProfile(p.id) } }
}

@Composable
fun NotesScreen(vm: AppVm, profileId: Long, notes: List<Note>, onBack: () -> Unit) {
    var edit by remember { mutableStateOf<Note?>(null) }
    SubScaffold("Notas", onBack, actions = {
        IconButton({ edit = Note(0, profileId, "", System.currentTimeMillis()) }) { Icon(Icons.Default.Add, "Nova nota") }
    }) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (notes.isEmpty()) item { Text("Anote sintomas, dúvidas para o médico, reações a remédios...", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(notes, key = { it.id }) { n ->
                AppCard(Modifier.fillMaxWidth(), onClick = { edit = n }) {
                    Text(n.createdAt.toLocal().let { it.format(fmtDate) + " " + it.format(fmtTime) }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(n.text)
                }
            }
        }
    }
    edit?.let { n ->
        var text by remember { mutableStateOf(n.text) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(if (n.id == 0L) "Nova nota" else "Editar nota") },
            text = { OutlinedTextField(text, { text = it }, minLines = 4, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton({ if (text.isNotBlank()) { vm.saveNote(n.copy(text = text.trim())); edit = null } }) { Text("Salvar") } },
            dismissButton = {
                Row {
                    if (n.id != 0L) TextButton({ vm.deleteNote(n); edit = null }) { Text("Apagar", color = MaterialTheme.colorScheme.error) }
                    TextButton({ edit = null }) { Text("Cancelar") }
                }
            },
        )
    }
}

@Composable
fun ReportScreen(profile: Profile?, meds: List<Medication>, logs: List<DoseLog>, appts: List<Appointment>, notes: List<Note>, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var days by remember { mutableIntStateOf(30) }
    val text = remember(meds, logs, appts, notes, days) { buildReport(profile, meds, logs, appts, notes, days) }
    SubScaffold("Relatório para o médico", onBack) {
        Column(Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChipRow(listOf(7 to "7 dias", 30 to "30 dias", 90 to "90 dias"), days) { days = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ sharePdf(ctx, text) }, Modifier.weight(1f)) { Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text("PDF") }
                FilledTonalButton({ shareText(ctx, text) }, Modifier.weight(1f)) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Texto") }
            }
            AppCard(Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(text.replace("# ", ""), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun StatusRow(ok: Boolean, title: String, desc: String, action: String, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = if (ok) androidx.compose.ui.graphics.Color(0xFF16A34A) else androidx.compose.ui.graphics.Color(0xFFF59E0B))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!ok) TextButton(onClick) { Text(action) }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppVm, tick: Int, requestNotif: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val p = vm.prefs
    SubScaffold("Ajustes e alertas", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Alertas confiáveis")
            StatusRow(canNotify(ctx), "Notificações", "Necessárias para os lembretes aparecerem.", "Permitir") {
                if (Build.VERSION.SDK_INT >= 33) requestNotif()
                else ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            StatusRow(ignoringBattery(ctx), "Sem otimização de bateria", "Evita que o sistema atrase ou bloqueie os alarmes.", "Desativar") { askIgnoreBattery(ctx) }
            StatusRow(canExact(ctx), "Alarmes exatos", "Permite avisar exatamente no horário.", "Permitir") {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            AppCard(Modifier.fillMaxWidth()) {
                Text("Dica para Samsung", fontWeight = FontWeight.SemiBold)
                Text("Abra Configurações > Bateria > Limites de uso em segundo plano > Apps que nunca entram em suspensão e adicione o Dosemed. Em Configurações > Aplicativos > Dosemed > Bateria, escolha \"Sem restrições\".",
                    style = MaterialTheme.typography.bodySmall)
                TextButton({ runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) { Text("Abrir info do app") }
            }
            OutlinedButton({
                showDoseNotification(ctx, Medication(id = -1, profileId = 0, name = "Remédio de teste", dose = "1 comprimido"), System.currentTimeMillis())
            }, Modifier.fillMaxWidth()) { Text("Enviar notificação de teste") }

            SectionTitle("Tema")
            ChipRow(listOf("system" to "Sistema", "light" to "Claro", "dark" to "Escuro", "amoled" to "AMOLED"), p.theme) { p.theme = it; p.save() }
            SectionTitle("Tempo de adiar")
            ChipRow(listOf(5 to "5 min", 10 to "10 min", 15 to "15 min", 30 to "30 min"), p.snoozeMin) { p.snoozeMin = it; p.save() }
            SectionTitle("Notificações")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Tela cheia"); Text("Abre o app sobre a tela bloqueada no horário da dose.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Switch(p.fullScreen, { p.fullScreen = it; p.save() })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Modo discreto"); Text("Esconde o nome do remédio na notificação.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Switch(p.discreet, { p.discreet = it; p.save() })
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
