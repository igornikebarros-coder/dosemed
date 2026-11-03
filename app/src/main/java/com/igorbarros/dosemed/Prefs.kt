package com.igorbarros.dosemed

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("dosemed", Context.MODE_PRIVATE)
    var theme by mutableStateOf(sp.getString("theme", "system") ?: "system")      // system, light, dark, amoled
    var snoozeMin by mutableStateOf(sp.getInt("snooze", 10))
    var fullScreen by mutableStateOf(sp.getBoolean("fullscreen", false))
    var discreet by mutableStateOf(sp.getBoolean("discreet", false))
    var profileId by mutableStateOf(sp.getLong("profile", 0L))

    fun save() = sp.edit()
        .putString("theme", theme).putInt("snooze", snoozeMin)
        .putBoolean("fullscreen", fullScreen).putBoolean("discreet", discreet)
        .putLong("profile", profileId).apply()
}
