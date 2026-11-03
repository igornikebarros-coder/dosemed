package com.igorbarros.dosemed

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

const val CH_DOSES = "doses"
const val CH_STOCK = "estoque"
const val CH_APPT = "consultas"

fun ensureChannels(ctx: Context) {
    val nm = ctx.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(CH_DOSES, "Horário dos remédios", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Lembretes de doses"; enableVibration(true)
    })
    nm.createNotificationChannel(NotificationChannel(CH_STOCK, "Estoque baixo", NotificationManager.IMPORTANCE_DEFAULT))
    nm.createNotificationChannel(NotificationChannel(CH_APPT, "Consultas", NotificationManager.IMPORTANCE_HIGH))
}

fun canNotify(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun notify(ctx: Context, id: Int, n: Notification) {
    if (!canNotify(ctx)) return
    runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
}

private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
    ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

fun doseNotifId(medId: Long, at: Long) = "$medId:$at".hashCode()

private fun actionPi(ctx: Context, kind: String, medId: Long, at: Long): PendingIntent {
    val i = Intent(ctx, ActionReceiver::class.java).setAction(kind)
        .setData(Uri.parse("dosemed://action/$kind/$medId/$at"))
        .putExtra("medId", medId).putExtra("at", at)
    return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

fun showDoseNotification(ctx: Context, m: Medication, at: Long) {
    ensureChannels(ctx)
    val prefs = Prefs(ctx)
    val title = if (prefs.discreet) "Hora do seu remédio" else "Hora de tomar ${m.name}"
    val text = if (prefs.discreet) "Toque para ver os detalhes" else
        listOf(m.dose, m.form).filter { it.isNotBlank() }.joinToString(" · ") + " · " + at.toLocal().format(fmtTime)
    val b = NotificationCompat.Builder(ctx, CH_DOSES)
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setContentTitle(title).setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setVisibility(if (prefs.discreet) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
        .setColor(m.color)
        .setContentIntent(openApp(ctx))
        .setAutoCancel(false)
        .addAction(0, "Tomei", actionPi(ctx, "TAKE", m.id, at))
        .addAction(0, "Adiar", actionPi(ctx, "SNOOZE", m.id, at))
        .addAction(0, "Pular", actionPi(ctx, "SKIP", m.id, at))
    if (prefs.fullScreen) b.setFullScreenIntent(openApp(ctx), true)
    notify(ctx, doseNotifId(m.id, at), b.build())
}

fun showLowStock(ctx: Context, m: Medication) {
    ensureChannels(ctx)
    val n = NotificationCompat.Builder(ctx, CH_STOCK)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Estoque baixo: ${m.name}")
        .setContentText("Restam ${m.stock ?: 0} unidades. Hora de repor.")
        .setContentIntent(openApp(ctx)).setAutoCancel(true).build()
    notify(ctx, 900000 + m.id.toInt(), n)
}

fun showApptNotification(ctx: Context, a: Appointment) {
    ensureChannels(ctx)
    val whenTxt = a.dateTime.toLocal().let { it.format(fmtDate) + " às " + it.format(fmtTime) }
    val n = NotificationCompat.Builder(ctx, CH_APPT)
        .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
        .setContentTitle("Consulta: ${a.title}")
        .setContentText(whenTxt + if (a.location.isNotBlank()) " · ${a.location}" else "")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(openApp(ctx)).setAutoCancel(true).build()
    notify(ctx, 800000 + a.id.toInt(), n)
}

object Alarms {
    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    private fun pi(ctx: Context, kind: String, key: String, extras: Intent.() -> Unit): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(kind).setData(Uri.parse("dosemed://alarm/$kind/$key")).apply(extras)
        return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun set(ctx: Context, at: Long, p: PendingIntent) {
        // setAlarmClock: exato, atravessa o modo Soneca e não depende de permissão extra.
        am(ctx).setAlarmClock(AlarmManager.AlarmClockInfo(at, openApp(ctx)), p)
    }

    fun scheduleMed(ctx: Context, m: Medication, after: Long) {
        val p = pi(ctx, "MED", "${m.id}") { putExtra("medId", m.id); }
        val next = if (m.active) nextAfter(m, after) else null
        if (next == null) { am(ctx).cancel(p); return }
        val p2 = pi(ctx, "MED", "${m.id}") { putExtra("medId", m.id); putExtra("at", next) }
        set(ctx, next, p2)
    }

    fun cancelMed(ctx: Context, medId: Long) {
        am(ctx).cancel(pi(ctx, "MED", "$medId") { putExtra("medId", medId) })
    }

    fun snooze(ctx: Context, medId: Long, at: Long, triggerAt: Long) {
        set(ctx, triggerAt, pi(ctx, "SNOOZE", "$medId") { putExtra("medId", medId); putExtra("at", at) })
    }

    fun scheduleAppt(ctx: Context, a: Appointment) {
        val p = pi(ctx, "APPT", "${a.id}") { putExtra("apptId", a.id) }
        val trigger = a.dateTime - a.remindMinutes * 60_000L
        if (a.done || a.remindMinutes <= 0 || trigger <= System.currentTimeMillis()) { am(ctx).cancel(p); return }
        set(ctx, trigger, p)
    }

    fun cancelAppt(ctx: Context, id: Long) { am(ctx).cancel(pi(ctx, "APPT", "$id") { putExtra("apptId", id) }) }

    /** Reagenda tudo (abrir app, editar, reiniciar). Com catchUp, avisa doses das últimas horas que ficaram sem registro. */
    suspend fun rescheduleAll(ctx: Context, catchUp: Boolean = false) {
        val dao = AppDb.get(ctx).dao()
        val now = System.currentTimeMillis()
        for (m in dao.medsNow()) {
            scheduleMed(ctx, m, now)
            if (catchUp && m.active) {
                val d = today()
                for (at in occurrencesOn(m, d.minusDays(1)) + occurrencesOn(m, d)) {
                    if (at <= now && now - at <= 2 * 3_600_000L && dao.log(m.id, at) == null) showDoseNotification(ctx, m, at)
                }
            }
        }
        for (a in dao.apptsNow()) scheduleAppt(ctx, a)
    }
}

object Repo {
    /** Registra a dose (TAKEN/SKIPPED) e ajusta o estoque. */
    suspend fun mark(ctx: Context, medId: Long, at: Long, status: String, note: String = "") {
        val dao = AppDb.get(ctx).dao()
        val m = dao.med(medId) ?: return
        val old = dao.log(medId, at)
        dao.insertLog(DoseLog(old?.id ?: 0, medId, at, status, System.currentTimeMillis(), note.ifBlank { old?.note ?: "" }))
        var stock = m.stock
        if (stock != null) {
            if (old?.status != "TAKEN" && status == "TAKEN") stock = maxOf(0, stock - m.unitsPerDose)
            if (old?.status == "TAKEN" && status != "TAKEN") stock += m.unitsPerDose
            if (stock != m.stock) {
                dao.setStock(medId, stock)
                if (stock <= m.lowStockAt && (m.stock ?: 0) > stock) showLowStock(ctx, m.copy(stock = stock))
            }
        }
        NotificationManagerCompat.from(ctx).cancel(doseNotifId(medId, at))
    }

    suspend fun undo(ctx: Context, medId: Long, at: Long) {
        val dao = AppDb.get(ctx).dao()
        val m = dao.med(medId) ?: return
        val old = dao.log(medId, at) ?: return
        dao.deleteLog(old.id)
        if (old.status == "TAKEN" && m.stock != null) dao.setStock(medId, m.stock + m.unitsPerDose)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDb.get(ctx).dao()
                when (intent.action) {
                    "MED", "SNOOZE" -> {
                        val medId = intent.getLongExtra("medId", -1)
                        val at = intent.getLongExtra("at", -1)
                        val m = dao.med(medId)
                        if (m != null && at > 0 && m.active && dao.log(medId, at) == null) showDoseNotification(ctx, m, at)
                        if (intent.action == "MED" && m != null) Alarms.scheduleMed(ctx, m, at)
                    }
                    "APPT" -> dao.appt(intent.getLongExtra("apptId", -1))?.let { if (!it.done) showApptNotification(ctx, it) }
                }
            } finally { pending.finish() }
        }
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        val medId = intent.getLongExtra("medId", -1)
        val at = intent.getLongExtra("at", -1)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    "TAKE" -> Repo.mark(ctx, medId, at, "TAKEN")
                    "SKIP" -> Repo.mark(ctx, medId, at, "SKIPPED")
                    "SNOOZE" -> {
                        NotificationManagerCompat.from(ctx).cancel(doseNotifId(medId, at))
                        Alarms.snooze(ctx, medId, at, System.currentTimeMillis() + Prefs(ctx).snoozeMin * 60_000L)
                    }
                }
            } finally { pending.finish() }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { Alarms.rescheduleAll(ctx, catchUp = intent.action == Intent.ACTION_BOOT_COMPLETED) } finally { pending.finish() }
        }
    }
}
