package top.nekoh2o.player.data.net

/** Small persistence boundary shared by Android and desktop provider sessions. */
interface ProviderPreferences {
    fun getString(key: String, fallback: String?): String?
    fun getBoolean(key: String, fallback: Boolean): Boolean
    fun edit(): Editor
    interface Editor {
        fun putString(key: String, value: String): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun remove(key: String): Editor
        fun apply()
    }
}
