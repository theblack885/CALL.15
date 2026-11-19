package com.aicallguard.app

import android.content.Context
import org.json.JSONArray

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
        trusted(c).split(",").map { it.trim() }.filter { it.isNotEmpty() }
            .any { it.equals(id, ignoreCase = true) }

    fun listenOn(c: Context): Boolean = sp(c).getBoolean("listen", false)
    fun setListenOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("listen", v).apply()

    fun btListenOn(c: Context): Boolean = sp(c).getBoolean("btlisten", false)
    fun setBtListenOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("btlisten", v).apply()

    // ---------- Sounds / Chat ----------
    fun soundsOn(c: Context): Boolean = sp(c).getBoolean("sounds", true)
    fun setSoundsOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("sounds", v).apply()

    fun lastRead(c: Context, peer: String): Long = sp(c).getLong("read_$peer", 0L)
    fun setLastRead(c: Context, peer: String, t: Long) = sp(c).edit().putLong("read_$peer", t).apply()

    fun lastNotified(c: Context): Long = sp(c).getLong("notified", 0L)
    fun setLastNotified(c: Context, t: Long) = sp(c).edit().putLong("notified", t).apply()

    fun extraPeers(c: Context): List<String> =
        (sp(c).getString("peers", "") ?: "").split(",").filter { it.isNotBlank() }

    fun addExtraPeer(c: Context, p: String) {
        val l = extraPeers(c).toMutableList()
        if (!l.contains(p)) {
            l.add(p)
            sp(c).edit().putString("peers", l.joinToString(",")).apply()
        }
    }

    // ---------- History ----------
    fun history(c: Context): List<String> =
        (sp(c).getString("hist", "") ?: "").split("\n").filter { it.isNotBlank() }

    fun addHistory(c: Context, line: String) {
        val list = history(c).toMutableList()
        list.add(0, line)
        while (list.size > 50) list.removeAt(list.lastIndex)
        sp(c).edit().putString("hist", list.joinToString("\n")).apply()
    }

    // ---------- Notes ----------
    fun notes(c: Context): List<String> = try {
        val a = JSONArray(sp(c).getString("notes", "[]") ?: "[]")
        List(a.length()) { a.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun saveNotes(c: Context, list: List<String>) {
        sp(c).edit().putString("notes", JSONArray(list).toString()).apply()
    }

    fun addNote(c: Context, text: String) {
        val l = notes(c).toMutableList()
        l.add(0, text)
        while (l.size > 100) l.removeAt(l.lastIndex)
        saveNotes(c, l)
    }

    fun deleteNote(c: Context, index: Int) {
        val l = notes(c).toMutableList()
        if (index in l.indices) {
            l.removeAt(index)
            saveNotes(c, l)
        }
    }
}
