package inc.flide.vim8.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import inc.flide.vim8.appPreferenceModel
import inc.flide.vim8.datastore.model.observeAsState
import inc.flide.vim8.ime.layout.AvailableLayouts
import inc.flide.vim8.layoutLoader
import inc.flide.vim8.lib.compose.AppTheme
import inc.flide.vim8.lib.compose.LocalKeyboardDatabaseController
import inc.flide.vim8.lib.compose.LocalPreviewFieldController
import inc.flide.vim8.lib.compose.PreviewKeyboardField
import inc.flide.vim8.lib.compose.ProvideLocalizedResources
import inc.flide.vim8.lib.compose.SystemUiApp
import inc.flide.vim8.lib.compose.rememberKeyboardDatabaseController
import inc.flide.vim8.lib.compose.rememberPreviewFieldController
import java.lang.ref.WeakReference

val LocalNavController = staticCompositionLocalOf<NavController> {
    error("LocalNavController not initialized")
}

var availableLayouts: WeakReference<AvailableLayouts?> = WeakReference(null)

class MainActivity : ComponentActivity() {
    private val prefs by appPreferenceModel()
    private var resourcesContext by mutableStateOf(this as Context)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().apply {
            setKeepOnScreenCondition { !prefs.isReady() }
        }
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        resourcesContext = createConfigurationContext(Configuration(resources.configuration))

        prefs.onReady(this) { isModelLoaded ->
            if (!isModelLoaded) return@onReady
            setContent {
                val prefs by appPreferenceModel()
                val colorScheme = prefs.theme.colorScheme()
                ProvideLocalizedResources(resourcesContext) {
                    AppTheme(colorScheme = colorScheme) {
                        Surface {
                            SystemUiApp()
                            AppContent()
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun AppContent() {
        val keyboardDatabaseController = rememberKeyboardDatabaseController()
        val navController = rememberNavController()
        val previewFieldController = rememberPreviewFieldController()
        val isImeSetUp by prefs.internal.isImeSetup.observeAsState()
        val context = LocalContext.current
        val layoutLoader by context.layoutLoader()

        if (availableLayouts.get() == null) {
            availableLayouts = WeakReference(AvailableLayouts(layoutLoader, context))
        }

        CompositionLocalProvider(
            LocalKeyboardDatabaseController provides keyboardDatabaseController,
            LocalNavController provides navController,
            LocalPreviewFieldController provides previewFieldController
        ) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Routes.AppNavHost(
                    modifier = Modifier.weight(1.0f),
                    navController = navController,
                    keyboardDatabaseController = keyboardDatabaseController,
                    startDestination = if (isImeSetUp) Routes.Settings.HOME else Routes.Setup.SCREEN
                )
                PreviewKeyboardField(previewFieldController)
            }
        }
        SideEffect {
            navController.setOnBackPressedDispatcher(this.onBackPressedDispatcher)
        }
    }
}
