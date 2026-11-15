package com.aicallguard.app

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("callapp", Context.MODE_PRIVATE)

    fun myId(c: Context): String {
        val s = sp(c)
        val old = s.getString("id", null)
        if (old != null) return old
        val id = (10000000..99999999).random().toString()
        s.edit().putString("id", id).apply()
        return id
    }

    fun name(c: Context): String = sp(c).getString("name", "") ?: ""
    fun setName(c: Context, v: String) = sp(c).edit().putString("name", v).apply()

    fun code(c: Context): String = sp(c).getString("code", "3050") ?: "3050"
    fun setCode(c: Context, v: String) = sp(c).edit().putString("code", v).apply()

    fun aiMode(c: Context): Boolean = sp(c).getBoolean("ai", true)
    fun setAiMode(c: Context, v: Boolean) = sp(c).edit().putBoolean("ai", v).apply()

    fun trusted(c: Context): String = sp(c).getString("trusted", "") ?: ""
    fun setTrusted(c: Context, v: String) = sp(c).edit().putString("trusted", v).apply()
    fun isTrusted(c: Context, id: String): Boolean =
        trusted(c).split(",").map { it.trim() }.filter { it.isNotEmpty() }.contains(id)

    fun listenOn(c: Context): Boolean = sp(c).getBoolean("listen", false)
    fun setListenOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("listen", v).apply()

    fun history(c: Context): List<String> =
        (sp(c).getString("hist", "") ?: "").split("\n").filter { it.isNotBlank() }

    fun addHistory(c: Context, line: String) {
        val list = history(c).toMutableList()
        list.add(0, line)
        while (list.size > 50) list.removeAt(list.lastIndex)
        sp(c).edit().putString("hist", list.joinToString("\n")).apply()
    }
}
