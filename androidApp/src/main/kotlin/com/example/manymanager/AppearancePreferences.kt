package com.example.manymanager

import android.content.Context

/** Appearance only. AppCompat/platform storage remains the sole owner of app locales. */
class AppearancePreferences(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("appearance", Context.MODE_PRIVATE)

    var dark: Boolean
        get() = preferences.getBoolean("dark", false)
        set(value) {
            preferences.edit().putBoolean("dark", value).apply()
        }
}
