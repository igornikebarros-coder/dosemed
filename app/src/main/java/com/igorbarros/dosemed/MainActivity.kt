@file:OptIn(ExperimentalMaterial3Api::class)
package com.igorbarros.dosemed

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

class MainActivity : ComponentActivity() {
    private val vm: AppVm by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureChannels(this)
        setContent {
            DosemedTheme(vm.prefs.theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { App(vm) }
            }
        }
    }
}

private sealed interface Sub {
    data class Med(val id: Long) : Sub
    data class Appt(val id: Long) : Sub
    data object Profiles : Sub
    data object Notes : Sub
    data object Report : Sub
    data object Settings : Sub
}

@Composable
fun App(vm: AppVm) {
    val ctx = LocalContext.current
    val profiles by vm.profiles.collectAsState()
    val allMeds by vm.meds.collectAsState()
    val allLogs by vm.logs.collectAsState()
    val allAppts by vm.appts.collectAsState()
    val allNotes by vm.notes.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sub by remember { mutableStateOf<Sub?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { tick++; vm.reschedule() } }
        owner.lifecycle.addObserver(ob); onDispose { owner.lifecycle.removeObserver(ob) }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val requestNotif = { if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    LaunchedEffect(Unit) { if (!canNotify(ctx)) requestNotif() }

    val profile = profiles.firstOrNull { it.id == vm.prefs.profileId } ?: profiles.firstOrNull()
    if (profile == null) { Box(Modifier.fillMaxSize()); return }
    val meds = allMeds.filter { it.profileId == profile.id }
    val ids = meds.map { it.id }.toSet()
    val logs = allLogs.filter { it.medId in ids }
    val appts = allAppts.filter { it.profileId == profile.id }
    val notes = allNotes.filter { it.profileId == profile.id }
    val setupNeeded = remember(tick) { !canNotify(ctx) || !ignoringBattery(ctx) }

    when (val s = sub) {
        is Sub.Med -> { MedEditScreen(vm, profile.id, allMeds.firstOrNull { it.id == s.id }) { sub = null }; return }
        is Sub.Appt -> { ApptEditScreen(vm, profile.id, allAppts.firstOrNull { it.id == s.id }) { sub = null }; return }
        Sub.Profiles -> { ProfilesScreen(vm, profiles, profile.id) { sub = null }; return }
        Sub.Notes -> { NotesScreen(vm, profile.id, notes) { sub = null }; return }
        Sub.Report -> { ReportScreen(profile, meds, logs, appts, notes) { sub = null }; return }
        Sub.Settings -> { SettingsScreen(vm, tick, requestNotif) { sub = null }; return }
        null -> {}
    }

    val tabs = listOf("Hoje" to Icons.Default.Today, "Remédios" to Icons.Default.Medication, "Histórico" to Icons.Default.QueryStats, "Consultas" to Icons.Default.EventNote, "Mais" to Icons.Default.MoreHoriz)
    Scaffold(bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            tabs.forEachIndexed { i, (label, icon) ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, label) }, label = { Text(label, maxLines = 1) })
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> TodayScreen(vm, profile, profiles, meds, logs, appts, setupNeeded, { sub = Sub.Settings }, { sub = Sub.Med(0) }, { tab = 3 })
                1 -> MedsScreen(vm, meds, { sub = Sub.Med(0) }) { sub = Sub.Med(it) }
                2 -> HistoryScreen(vm, meds, logs)
                3 -> ApptScreen(vm, appts, { sub = Sub.Appt(0) }) { sub = Sub.Appt(it) }
                else -> MoreScreen {
                    sub = when (it) { "profiles" -> Sub.Profiles; "notes" -> Sub.Notes; "report" -> Sub.Report; else -> Sub.Settings }
                }
            }
        }
    }
}
