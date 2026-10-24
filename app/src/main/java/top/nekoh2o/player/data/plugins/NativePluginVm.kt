package top.nekoh2o.player.data.plugins

import androidx.annotation.Keep

@Keep
class NativePluginVm {
    external fun create(): Long
    external fun eval(handle: Long, script: String): String
    external fun close(handle: Long)

    companion object {
        init {
            System.loadLibrary("nekoplayer_plugins")
        }
    }
}
