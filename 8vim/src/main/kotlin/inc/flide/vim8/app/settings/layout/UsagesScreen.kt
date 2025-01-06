package inc.flide.vim8.app.settings.layout

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import arrow.core.None
import arrow.core.Option
import inc.flide.vim8.R
import inc.flide.vim8.app.LocalNavController
import inc.flide.vim8.app.Routes
import inc.flide.vim8.datastore.model.PreferenceModel
import inc.flide.vim8.datastore.ui.Preference
import inc.flide.vim8.datastore.ui.PreferenceGroup
import inc.flide.vim8.datastore.ui.PreferenceUiScope
import inc.flide.vim8.ime.layout.models.LayerLevel
import inc.flide.vim8.ime.layout.models.yaml.versions.common.Contact
import inc.flide.vim8.ime.layout.models.yaml.versions.common.isEmpty
import inc.flide.vim8.lib.android.launchUrl
import inc.flide.vim8.lib.compose.Dialog
import inc.flide.vim8.lib.compose.LocalKeyboardDatabaseController
import inc.flide.vim8.lib.compose.Screen
import inc.flide.vim8.lib.compose.stringRes

@Composable
fun UsagesScreen() = Screen {
    val navController = LocalNavController.current
    val keyboardDatabaseController = LocalKeyboardDatabaseController.current

    var currentLayoutFilter by remember { mutableStateOf<Option<LayerLevel>>(None) }

    title = stringRes(
        R.string.settings__layouts__usages__title,
        "layout" to keyboardDatabaseController.layoutName()
    )

    content {
        Card(modifier = Modifier.padding(8.dp)) {
        }
        PreferenceGroup(title = "Layout: ${keyboardDatabaseController.layoutName()}") {
            if (keyboardDatabaseController.info()?.isEmpty() == false) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Description(description = keyboardDatabaseController.info()!!.description)
                    Author(contact = keyboardDatabaseController.info()!!.contact)
                }
            }
        }
        PreferenceGroup(title = "Filter") {
            FilterLayer {
                currentLayoutFilter = it
            }
        }

        keyboardDatabaseController
            .byLayer(currentLayoutFilter).forEach {
                Preference(
                    title = keyboardDatabaseController.action(it),
                    summary = "Layer: ${keyboardDatabaseController.layer(it)}\nMovement: ${
                        keyboardDatabaseController.movementSequence(
                            it
                        )
                    }",
                    onClick = { navController.navigate(Routes.Settings.layoutUsage(it)) },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                    }
                )
            }
    }
}

@Composable
private fun <T : PreferenceModel> PreferenceUiScope<T>.FilterLayer(
    onSelect: (Option<LayerLevel>) -> Unit
) {
    val keyboardDatabaseController = LocalKeyboardDatabaseController.current
    var idx by remember { mutableIntStateOf(0) }
    var selectedText by remember { mutableStateOf(keyboardDatabaseController.layer(0)) }

    Dialog {
        title = "Layer"
        index = { idx }
        items = { keyboardDatabaseController.layers() }
        onConfirm {
            idx = it
            selectedText = keyboardDatabaseController.layer(it)
            onSelect(keyboardDatabaseController.layer(selectedText))
        }
        Preference(
            title = "Layer filter",
            summary = selectedText,
            onClick = { show() },
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
private fun Description(description: String) {
    if (description.isNotEmpty()) {
        Text(text = description)
    }
}

@Composable
private fun Author(contact: Contact) {
    val context = LocalContext.current
    if (!contact.isEmpty()) {
        val name =
            contact.name.ifEmpty { contact.email }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                modifier = if (contact.email.isNotEmpty()) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClick = {
                            context.launchUrl("mailto:${contact.email}") {
                                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        }
                    )
                } else {
                    Modifier
                },
                text = "by $name"
            )
        }
    }
}
