package top.nekoh2o.player.data.net

import android.content.SharedPreferences

class AndroidProviderPreferences(private val prefs: SharedPreferences) : ProviderPreferences {
    override fun getString(key: String, fallback: String?) = prefs.getString(key, fallback)
    override fun getBoolean(key: String, fallback: Boolean) = prefs.getBoolean(key, fallback)
    override fun edit(): ProviderPreferences.Editor = Editor(prefs.edit())
    private class Editor(private val editor: SharedPreferences.Editor) : ProviderPreferences.Editor {
        override fun putString(key: String, value: String) = apply { editor.putString(key, value) }
        override fun putBoolean(key: String, value: Boolean) = apply { editor.putBoolean(key, value) }
        override fun remove(key: String) = apply { editor.remove(key) }
        override fun apply() { editor.apply() }
    }
}
