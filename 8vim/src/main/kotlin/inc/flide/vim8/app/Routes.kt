package inc.flide.vim8.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import inc.flide.vim8.app.settings.BackupRestoreScreen
import inc.flide.vim8.app.settings.ClipboardScreen
import inc.flide.vim8.app.settings.GestureScreen
import inc.flide.vim8.app.settings.HomeScreen
import inc.flide.vim8.app.settings.KeyboardScreen
import inc.flide.vim8.app.settings.ThemeScreen
import inc.flide.vim8.app.settings.about.AboutScreen
import inc.flide.vim8.app.settings.about.ThirdPartyLicencesScreen
import inc.flide.vim8.app.settings.layout.LayoutScreen
import inc.flide.vim8.app.settings.layout.UsageScreen
import inc.flide.vim8.app.settings.layout.UsagesScreen
import inc.flide.vim8.app.setup.SetupScreen
import inc.flide.vim8.lib.compose.ActionMapDatabase
import inc.flide.vim8.lib.compose.KeyboardDatabaseController
import inc.flide.vim8.lib.kotlin.curlyFormat

object Routes {
    object Setup {
        const val SCREEN = "setup"
    }

    object Settings {
        const val HOME = "settings"
        const val LAYOUTS = "settings/layouts"
        const val LAYOUT_USAGES = "settings/layouts/usages"
        const val LAYOUT_USAGE = "settings/layouts/usages/{id}"
        fun layoutUsage(item: ActionMapDatabase.Item) = LAYOUT_USAGE.curlyFormat("id" to item.index)

        const val KEYBOARD = "settings/keyboard"
        const val CLIPBOARD = "settings/clipboard"
        const val THEME = "settings/theme"
        const val GESTURE = "settings/gesture"
        const val BACKUP_AND_RESTORE = "settings/backup-and-restore"
        const val ABOUT = "settings/about"
        const val THIRD_PARTY_LICENSES = "settings/about/third-party-licenses"
    }

    @Composable
    fun AppNavHost(
        modifier: Modifier,
        navController: NavHostController,
        keyboardDatabaseController: KeyboardDatabaseController,
        startDestination: String
    ) {
        NavHost(
            modifier = modifier,
            navController = navController,
            startDestination = startDestination
        ) {
            composable(Setup.SCREEN) { SetupScreen() }

            composable(Settings.HOME) { HomeScreen() }
            composable(Settings.LAYOUTS) { LayoutScreen() }
            composable(Settings.LAYOUT_USAGES) { UsagesScreen() }
            composable(Settings.LAYOUT_USAGE) { navBackStack ->
                val id = navBackStack.arguments?.getString("id")?.let { id ->
                    keyboardDatabaseController.byId(id)
                }
                UsageScreen(id)
            }
            composable(Settings.KEYBOARD) { KeyboardScreen() }
            composable(Settings.CLIPBOARD) { ClipboardScreen() }
            composable(Settings.THEME) { ThemeScreen() }
            composable(Settings.GESTURE) { GestureScreen() }
            composable(Settings.BACKUP_AND_RESTORE) { BackupRestoreScreen() }
            composable(Settings.ABOUT) { AboutScreen() }
            composable(Settings.THIRD_PARTY_LICENSES) { ThirdPartyLicencesScreen() }
        }
    }
}
