package com.igorbarros.dosemed

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

val PT = Locale("pt", "BR")
val zone: ZoneId get() = ZoneId.systemDefault()
val fmtTime: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", PT)
val fmtDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT)
val fmtDay: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT)
val WEEK = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
val FORMS = listOf("Comprimido", "Cápsula", "Gotas", "Xarope", "Injeção", "Pomada", "Spray", "Adesivo", "Sachê", "Outro")
val PALETTE = listOf(0xFF12A594, 0xFF3B82F6, 0xFF8B5CF6, 0xFFEC4899, 0xFFEF4444, 0xFFF59E0B, 0xFF84CC16, 0xFF64748B).map { it.toInt() }

fun millisOf(day: LocalDate, t: LocalTime): Long = day.atTime(t).atZone(zone).toInstant().toEpochMilli()
fun Long.toLocal(): LocalDateTime = Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()
fun today(): LocalDate = LocalDate.now(zone)

fun parseTimes(s: String): List<LocalTime> =
    s.split(",").mapNotNull { runCatching { LocalTime.parse(it.trim()) }.getOrNull() }.distinct().sorted()

fun Medication.timeList(): List<LocalTime> {
    val l = parseTimes(times)
    return if (type == "INTERVAL") l.take(1).ifEmpty { listOf(LocalTime.of(8, 0)) } else l
}

/** Horários (epoch millis) previstos para o dia. */
fun occurrencesOn(m: Medication, day: LocalDate): List<Long> {
    if (!m.active || m.type == "ASNEEDED") return emptyList()
    val ed = day.toEpochDay()
    if (ed < m.startDate) return emptyList()
    if (m.endDate != null && ed > m.endDate) return emptyList()
    return when (m.type) {
        "DAILY" -> m.timeList().map { millisOf(day, it) }
        "WEEKDAYS" -> if (((m.weekdays shr (day.dayOfWeek.value - 1)) and 1) == 1) m.timeList().map { millisOf(day, it) } else emptyList()
        "INTERVAL" -> {
            val step = maxOf(1, m.intervalHours) * 3_600_000L
            val anchor = millisOf(LocalDate.ofEpochDay(m.startDate), m.timeList().first())
            val ds = millisOf(day, LocalTime.MIDNIGHT)
            val de = millisOf(day.plusDays(1), LocalTime.MIDNIGHT)
            var k = if (ds > anchor) (ds - anchor + step - 1) / step else 0L
            val out = mutableListOf<Long>()
            while (true) {
                val t = anchor + k * step
                if (t >= de) break
                out += t; k++
            }
            out
        }
        else -> emptyList()
    }
}

fun nextAfter(m: Medication, after: Long): Long? {
    val d0 = after.toLocal().toLocalDate()
    for (i in 0..400) {
        val n = occurrencesOn(m, d0.plusDays(i.toLong())).firstOrNull { it > after }
        if (n != null) return n
    }
    return null
}

fun scheduleSummary(m: Medication): String {
    val ts = m.timeList().joinToString(", ") { it.format(fmtTime) }
    val base = when (m.type) {
        "DAILY" -> "Todos os dias · $ts"
        "WEEKDAYS" -> WEEK.filterIndexed { i, _ -> ((m.weekdays shr i) and 1) == 1 }.joinToString(" ") + " · $ts"
        "INTERVAL" -> "A cada ${m.intervalHours} h a partir de ${m.timeList().first().format(fmtTime)}"
        else -> "Se necessário"
    }
    val dur = if (m.type == "ASNEEDED") "" else if (m.endDate == null) " · uso contínuo" else " · até ${LocalDate.ofEpochDay(m.endDate).format(fmtDate)}"
    return base + dur
}

const val MISSED_AFTER_MS = 6 * 3_600_000L

enum class DoseState(val label: String) { TAKEN("Tomada"), SKIPPED("Pulada"), MISSED("Perdida"), LATE("Atrasada"), PENDING("Pendente") }

data class DoseItem(val med: Medication, val at: Long, val log: DoseLog?, val state: DoseState)

fun stateOf(at: Long, log: DoseLog?, now: Long): DoseState = when {
    log?.status == "TAKEN" -> DoseState.TAKEN
    log?.status == "SKIPPED" -> DoseState.SKIPPED
    at > now -> DoseState.PENDING
    now - at > MISSED_AFTER_MS -> DoseState.MISSED
    else -> DoseState.LATE
}

/** Doses previstas no intervalo de dias, já cruzadas com o registro. Inclui registros avulsos (se necessário). */
fun doseItems(meds: List<Medication>, logs: List<DoseLog>, from: LocalDate, to: LocalDate, now: Long, withExtras: Boolean = true): List<DoseItem> {
    val byKey = logs.associateBy { it.medId to it.scheduledAt }
    val used = HashSet<Pair<Long, Long>>()
    val out = mutableListOf<DoseItem>()
    var d = from
    while (!d.isAfter(to)) {
        for (m in meds) for (at in occurrencesOn(m, d)) {
            val l = byKey[m.id to at]; if (l != null) used += m.id to at
            out += DoseItem(m, at, l, stateOf(at, l, now))
        }
        d = d.plusDays(1)
    }
    if (withExtras) {
        val ds = millisOf(from, LocalTime.MIDNIGHT); val de = millisOf(to.plusDays(1), LocalTime.MIDNIGHT)
        val mm = meds.associateBy { it.id }
        for (l in logs) {
            if ((l.medId to l.scheduledAt) in used || l.scheduledAt < ds || l.scheduledAt >= de) continue
            val m = mm[l.medId] ?: continue
            out += DoseItem(m, l.scheduledAt, l, stateOf(l.scheduledAt, l, now))
        }
    }
    return out.sortedBy { it.at }
}

data class Stats(val taken: Int, val skipped: Int, val missed: Int) {
    val total get() = taken + skipped + missed
    val pct get() = if (total == 0) -1 else taken * 100 / total
}

fun statsOf(items: List<DoseItem>): Stats = Stats(
    items.count { it.state == DoseState.TAKEN },
    items.count { it.state == DoseState.SKIPPED },
    items.count { it.state == DoseState.MISSED },
)
