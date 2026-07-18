package top.nekoh2o.player

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import top.nekoh2o.player.ui.PlayerViewModel
import top.nekoh2o.player.ui.nav.MainScaffold
import top.nekoh2o.player.ui.theme.NekoTheme

class MainActivity : ComponentActivity() {

    private val vm: PlayerViewModel by viewModels()

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val ssoLogin =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) vm.onSsoLoggedIn()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            NekoTheme {
                var fullPlayer by remember { mutableStateOf(false) }

                MainScaffold(
                    vm = vm,
                    onOpenFullPlayer = { fullPlayer = true },
                    onStartSsoLogin = { ssoLogin.launch(Intent(this, LoginActivity::class.java)) }
                )

                AnimatedVisibility(
                    visible = fullPlayer,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    top.nekoh2o.player.ui.screens.FullPlayerScreen(
                        vm = vm,
                        onClose = { fullPlayer = false }
                    )
                }
                if (fullPlayer) BackHandler { fullPlayer = false }
            }
        }
    }
}
