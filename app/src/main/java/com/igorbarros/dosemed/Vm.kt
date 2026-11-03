package com.igorbarros.dosemed

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class AppVm(private val app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    val prefs = Prefs(app)
    val profiles = dao.profiles().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val meds = dao.meds().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val logs = dao.logs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val appts = dao.appts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val notes = dao.notes().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    var ready by mutableStateOf(false)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (dao.profileCount() == 0) dao.insertProfile(Profile(name = "Eu", color = PALETTE[0]))
            ready = true
            Alarms.rescheduleAll(app)
        }
    }

    fun activeProfile(): Profile? = profiles.value.firstOrNull { it.id == prefs.profileId } ?: profiles.value.firstOrNull()
    fun selectProfile(id: Long) { prefs.profileId = id; prefs.save() }
    private fun io(block: suspend () -> Unit) = viewModelScope.launch(Dispatchers.IO) { block() }

    // Perfis
    fun saveProfile(p: Profile) = io { if (p.id == 0L) dao.insertProfile(p).also { selectProfile(it) } else dao.updateProfile(p) }
    fun deleteProfile(id: Long) = io {
        for (m in dao.medsNow().filter { it.profileId == id }) { Alarms.cancelMed(app, m.id); dao.deleteLogsOfMed(m.id); dao.deleteMed(m.id) }
        for (a in dao.apptsNow().filter { it.profileId == id }) { Alarms.cancelAppt(app, a.id); dao.deleteAppt(a.id) }
        notes.value.filter { it.profileId == id }.forEach { dao.deleteNote(it.id) }
        dao.deleteProfile(id)
        if (prefs.profileId == id) selectProfile(0)
    }

    // Remédios
    fun saveMed(m: Medication) = io {
        val id = if (m.id == 0L) dao.insertMed(m) else { dao.updateMed(m); m.id }
        dao.med(id)?.let { Alarms.scheduleMed(app, it, System.currentTimeMillis()) }
    }
    fun deleteMed(m: Medication) = io {
        Alarms.cancelMed(app, m.id); dao.deleteLogsOfMed(m.id); dao.deleteMed(m.id)
        m.photoPath?.let { File(it).delete() }
    }
    fun refill(m: Medication, qty: Int) = io { dao.setStock(m.id, (m.stock ?: 0) + qty) }

    // Doses
    fun mark(medId: Long, at: Long, status: String, note: String = "") = io { Repo.mark(app, medId, at, status, note) }
    fun undo(medId: Long, at: Long) = io { Repo.undo(app, medId, at) }
    fun takeNow(m: Medication) = io { Repo.mark(app, m.id, System.currentTimeMillis(), "TAKEN") }

    // Consultas
    fun saveAppt(a: Appointment) = io {
        val id = if (a.id == 0L) dao.insertAppt(a) else { dao.updateAppt(a); a.id }
        dao.appt(id)?.let { Alarms.scheduleAppt(app, it) }
    }
    fun deleteAppt(a: Appointment) = io { Alarms.cancelAppt(app, a.id); dao.deleteAppt(a.id) }

    // Notas
    fun saveNote(n: Note) = io { if (n.id == 0L) dao.insertNote(n) else dao.updateNote(n) }
    fun deleteNote(n: Note) = io { dao.deleteNote(n.id) }

    /** Copia a foto escolhida para o armazenamento do app (reduzida). */
    fun importPhoto(uri: Uri, onDone: (String?) -> Unit) = io {
        val path = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var s = 1
            while (bounds.outWidth / s > 800 || bounds.outHeight / s > 800) s *= 2
            val bmp = app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
            val dir = File(app.filesDir, "photos").apply { mkdirs() }
            val f = File(dir, "${System.currentTimeMillis()}.jpg")
            f.outputStream().use { bmp!!.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            f.absolutePath
        }.getOrNull()
        kotlinx.coroutines.withContext(Dispatchers.Main) { onDone(path) }
    }

    fun reschedule() = io { Alarms.rescheduleAll(app) }
}
