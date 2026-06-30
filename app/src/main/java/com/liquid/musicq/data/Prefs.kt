package com.liquid.musicq.data

import android.content.Context

/** Tiny SharedPreferences-backed store for the cookie and default quality. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("musicq", Context.MODE_PRIVATE)

    var cookie: String
        get() = sp.getString("cookie", "") ?: ""
        set(v) { sp.edit().putString("cookie", v).apply() }

    var qualityOrdinal: Int
        get() = sp.getInt("quality", 1)
        set(v) { sp.edit().putInt("quality", v).apply() }

    var autoEnhance: Boolean
        get() = sp.getBoolean("autoEnhance", true)
        set(v) { sp.edit().putBoolean("autoEnhance", v).apply() }

    /** When true, search/stream via QQ Music (needs cookie); else iTunes samples. */
    var useQQ: Boolean
        get() = sp.getBoolean("useQQ", false)
        set(v) { sp.edit().putBoolean("useQQ", v).apply() }
}
