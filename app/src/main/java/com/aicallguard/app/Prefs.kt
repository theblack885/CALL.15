package com.aicallguard.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("guard", Context.MODE_PRIVATE)

    fun code(c: Context): String = sp(c).getString("code", "3050") ?: "3050"
    fun setCode(c: Context, v: String) = sp(c).edit().putString("code", v).apply()

    fun owner(c: Context): String = sp(c).getString("owner", "मालिक") ?: "मालिक"
    fun setOwner(c: Context, v: String) = sp(c).edit().putString("owner", v).apply()
}

object GuardState {
    var active by mutableStateOf(false)
}

object CallLog {
    val lines = mutableStateListOf<String>()
    fun add(s: String) {
        lines.add(0, s)
        if (lines.size > 40) lines.removeAt(lines.lastIndex)
    }
}
