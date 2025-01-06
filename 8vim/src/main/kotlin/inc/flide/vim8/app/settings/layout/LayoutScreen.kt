package inc.flide.vim8.app.settings.layout

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import arrow.core.None
import arrow.core.some
import inc.flide.vim8.R
import inc.flide.vim8.app.LocalNavController
import inc.flide.vim8.app.Routes
import inc.flide.vim8.app.availableLayouts
import inc.flide.vim8.appPreferenceModel
import inc.flide.vim8.datastore.ui.Preference
import inc.flide.vim8.datastore.ui.PreferenceGroup
import inc.flide.vim8.ime.layout.CustomLayout
import inc.flide.vim8.ime.layout.loadKeyboardData
import inc.flide.vim8.ime.layout.models.KeyboardData
import inc.flide.vim8.ime.layout.models.error.ExceptionWrapperError
import inc.flide.vim8.ime.layout.models.error.LayoutError
import inc.flide.vim8.layoutLoader
import inc.flide.vim8.lib.compose.Dialog
import inc.flide.vim8.lib.compose.LocalKeyboardDatabaseController
import inc.flide.vim8.lib.compose.Screen
import inc.flide.vim8.lib.compose.stringRes
import inc.flide.vim8.lib.util.DialogsHelper.showAlert

private val emptyLayoutError =
    R.string.dialog__yaml__error__title to "The layout requires at least one layer"

@Composable
fun LayoutScreen() = Screen {
    title = stringRes(R.string.settings__layouts__title)
    previewFieldVisible = true

    val navController = LocalNavController.current
    val keyboardDatabaseController = LocalKeyboardDatabaseController.current
    val fileSelector = fileSelector()

    content {
        PreferenceGroup {
            Dialog {
                title = stringRes(R.string.select_preferred_keyboard_layout_dialog_title)
                index = { availableLayouts.get()?.index ?: 0 }
                items = { availableLayouts.get()?.displayNames ?: emptyList() }
                onConfirm { availableLayouts.get()?.selectLayout(it) }
                Preference(
                    title = stringRes(R.string.settings__layouts__select__title),
                    summary = stringRes(R.string.settings__layouts__select__summary),
                    onClick = { show() },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                    }
                )
            }
            Preference(
                title = stringRes(R.string.settings__layouts__load_custom__title),
                summary = stringRes(R.string.settings__layouts__load_custom__summary),
                onClick = { fileSelector() },
                trailing = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null
                    )
                }
            )
        }
        Preference(
            iconId = R.drawable.ic_keyboard,
            title = stringRes(
                R.string.settings__layouts__usages__title,
                "layout" to keyboardDatabaseController.layoutName()
            ),
            onClick = { navController.navigate(Routes.Settings.LAYOUT_USAGES) },
            trailing = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null
                )
            }
        )
    }
}

@Composable
private fun fileSelector(): () -> Unit {
    val context = LocalContext.current
    val prefs by appPreferenceModel()
    val layoutLoader by context.layoutLoader()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) {
        if (it == null) {
            return@rememberLauncherForActivityResult
        }
        context.contentResolver
            .takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val layout = CustomLayout(it)
        val currentHistory = prefs.layout.custom.history.get()
        val isInHistory = currentHistory.contains(it.toString())
        if (isInHistory) {
            availableLayouts.get()!!.updateKeyboardData(layout)
        } else {
            layout.loadKeyboardData(layoutLoader, context)
                .fold({ error: LayoutError ->
                    val title = if (error is ExceptionWrapperError) {
                        R.string.dialog__error__title
                    } else {
                        R.string.dialog__yaml__error__title
                    }
                    (title to error.message).some()
                }, { keyboardData: KeyboardData ->
                    if (keyboardData.totalLayers == 0) {
                        emptyLayoutError.some()
                    } else {
                        None
                    }
                })
                .onSome { (titleId, message) -> showAlert(context, titleId, message) }
                .onNone {
                    prefs.layout.current.set(layout)
                    val history =
                        listOf(it.toString()) + currentHistory.toList()

                    prefs.layout.custom.history.set(LinkedHashSet(history))
                }
        }
    }
    return { launcher.launch(arrayOf("application/octet-stream")) }
}
