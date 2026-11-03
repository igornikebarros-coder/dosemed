package com.igorbarros.dosemed

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File

fun buildReport(profile: Profile?, meds: List<Medication>, logs: List<DoseLog>, appts: List<Appointment>, notes: List<Note>, days: Int): String {
    val now = System.currentTimeMillis()
    val to = today(); val from = to.minusDays(days - 1L)
    val items = doseItems(meds, logs, from, to, now)
    val sb = StringBuilder()
    sb.appendLine("# Resumo de medicamentos")
    sb.appendLine("Paciente: ${profile?.name ?: "-"}")
    sb.appendLine("Período: ${from.format(fmtDate)} a ${to.format(fmtDate)} ($days dias)")
    sb.appendLine("Gerado em ${to.format(fmtDate)} pelo Dosemed")
    sb.appendLine()
    val st = statsOf(items)
    sb.appendLine("# Adesão geral")
    sb.appendLine(if (st.pct < 0) "Sem doses no período." else "${st.pct}% — ${st.taken} tomadas, ${st.skipped} puladas, ${st.missed} perdidas")
    sb.appendLine()
    sb.appendLine("# Medicamentos")
    if (meds.isEmpty()) sb.appendLine("Nenhum medicamento cadastrado.")
    for (m in meds) {
        val s = statsOf(items.filter { it.med.id == m.id })
        sb.appendLine("• ${m.name}${if (m.dose.isNotBlank()) " ${m.dose}" else ""} (${m.form})${if (!m.active) " [pausado]" else ""}")
        sb.appendLine("   ${scheduleSummary(m)}")
        if (s.pct >= 0) sb.appendLine("   Adesão: ${s.pct}% (${s.taken} tomadas, ${s.skipped} puladas, ${s.missed} perdidas)")
        if (m.stock != null) sb.appendLine("   Estoque: ${m.stock} unidades")
        if (m.notes.isNotBlank()) sb.appendLine("   Obs.: ${m.notes}")
    }
    sb.appendLine()
    sb.appendLine("# Registro de doses")
    val past = items.filter { it.state != DoseState.PENDING }
    if (past.isEmpty()) sb.appendLine("Sem registros.")
    for (i in past.asReversed()) {
        val t = i.at.toLocal()
        sb.appendLine("${t.format(fmtDate)} ${t.format(fmtTime)} — ${i.med.name}: ${i.state.label}" + (i.log?.note?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""))
    }
    sb.appendLine()
    sb.appendLine("# Consultas")
    if (appts.isEmpty()) sb.appendLine("Nenhuma consulta.")
    for (a in appts.sortedBy { it.dateTime }) {
        val t = a.dateTime.toLocal()
        sb.appendLine("${t.format(fmtDate)} ${t.format(fmtTime)} — ${a.title}" + listOf(a.doctor, a.location).filter { it.isNotBlank() }.joinToString("") { ", $it" } + if (a.done) " [realizada]" else "")
    }
    if (notes.isNotEmpty()) {
        sb.appendLine(); sb.appendLine("# Notas")
        for (n in notes) sb.appendLine("${n.createdAt.toLocal().format(fmtDate)} — ${n.text}")
    }
    sb.appendLine()
    sb.appendLine("Este resumo é um registro pessoal e não substitui orientação médica.")
    return sb.toString()
}

fun shareText(ctx: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(i, "Compartilhar relatório").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun sharePdf(ctx: Context, text: String) {
    val doc = PdfDocument()
    val w = 595; val h = 842; val margin = 40f
    val normal = Paint().apply { textSize = 11f; isAntiAlias = true }
    val bold = Paint().apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true; color = 0xFF0B7A6E.toInt() }
    var pageNo = 1
    var page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
    var y = margin + 10
    fun newPage() { doc.finishPage(page); pageNo++; page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create()); y = margin + 10 }
    for (raw in text.lines()) {
        val head = raw.startsWith("# ")
        val line = if (head) raw.removePrefix("# ") else raw
        val paint = if (head) bold else normal
        if (head) y += 8
        // quebra de linha simples
        var rest = line
        do {
            val n = paint.breakText(rest, true, w - 2 * margin, null).coerceAtLeast(1)
            if (y > h - margin) newPage()
            page.canvas.drawText(rest.substring(0, minOf(n, rest.length)), margin, y, paint)
            y += if (head) 20f else 15f
            rest = rest.drop(n)
        } while (rest.isNotEmpty())
    }
    doc.finishPage(page)
    val dir = File(ctx.cacheDir, "reports").apply { mkdirs() }
    val f = File(dir, "dosemed-relatorio.pdf")
    f.outputStream().use { doc.writeTo(it) }; doc.close()
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    val i = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(i, "Compartilhar PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
