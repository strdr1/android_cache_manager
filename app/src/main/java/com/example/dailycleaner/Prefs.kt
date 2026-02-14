package com.example.dailycleaner

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "settings"
    private const val KEY_AUTO = "auto_enabled"
    private const val KEY_THUMBS = "thumbs"
    private const val KEY_DLTMP = "dltmp"
    private const val KEY_DATA = "data"
    private const val KEY_MEDIA = "media"
    private const val KEY_APP = "app"
    private const val KEY_INTERVAL_H = "interval_h"
    private const val KEY_URI_DATA = "uri_data"
    private const val KEY_URI_MEDIA = "uri_media"
    private const val KEY_ONBOARD = "onboard_done"
    private const val KEY_ASKED_ALL = "asked_all_files_once"

    fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun loadConfig(ctx: Context): CleanerConfig {
        val p = prefs(ctx)
        return CleanerConfig(
            thumbnails = p.getBoolean(KEY_THUMBS, true),
            downloadTemps = p.getBoolean(KEY_DLTMP, true),
            androidDataCaches = p.getBoolean(KEY_DATA, true),
            androidMediaCaches = p.getBoolean(KEY_MEDIA, true),
            appCache = p.getBoolean(KEY_APP, true)
        )
    }

    fun saveConfig(ctx: Context, c: CleanerConfig) {
        prefs(ctx).edit()
            .putBoolean(KEY_THUMBS, c.thumbnails)
            .putBoolean(KEY_DLTMP, c.downloadTemps)
            .putBoolean(KEY_DATA, c.androidDataCaches)
            .putBoolean(KEY_MEDIA, c.androidMediaCaches)
            .putBoolean(KEY_APP, c.appCache)
            .apply()
    }

    fun getIntervalHours(ctx: Context): Long = prefs(ctx).getLong(KEY_INTERVAL_H, 24L)
    fun setIntervalHours(ctx: Context, h: Long) { prefs(ctx).edit().putLong(KEY_INTERVAL_H, h).apply() }

    fun setAutoEnabled(ctx: Context, enabled: Boolean) { prefs(ctx).edit().putBoolean(KEY_AUTO, enabled).apply() }
    fun isAutoEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_AUTO, false)

    fun setDataTreeUri(ctx: Context, uri: String) { prefs(ctx).edit().putString(KEY_URI_DATA, uri).apply() }
    fun getDataTreeUri(ctx: Context): String? = prefs(ctx).getString(KEY_URI_DATA, null)
    fun setMediaTreeUri(ctx: Context, uri: String) { prefs(ctx).edit().putString(KEY_URI_MEDIA, uri).apply() }
    fun getMediaTreeUri(ctx: Context): String? = prefs(ctx).getString(KEY_URI_MEDIA, null)
    fun setOnboardDone(ctx: Context, done: Boolean) { prefs(ctx).edit().putBoolean(KEY_ONBOARD, done).apply() }
    fun isOnboardDone(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ONBOARD, false)

    fun setAskedAllFilesOnce(ctx: Context, asked: Boolean) { prefs(ctx).edit().putBoolean(KEY_ASKED_ALL, asked).apply() }
    fun wasAskedAllFilesOnce(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ASKED_ALL, false)
}
