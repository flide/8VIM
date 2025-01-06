package inc.flide.vim8.lib.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import inc.flide.vim8.R
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KClass

fun Context.launchUrl(url: String, intentModifier: (Intent) -> Unit = { }) {
    val intent = Intent().also {
        it.action = Intent.ACTION_VIEW
        it.data = Uri.parse(url)
    }
    try {
        intentModifier(intent)
        this.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(
            this,
            this.stringRes(R.string.general__no_browser_app_found_for_url, "url" to url),
            Toast.LENGTH_LONG
        ).show()
    }
}
inline fun <T : Any> Context.launchActivity(
    kClass: KClass<T>,
    intentModifier: (Intent) -> Unit = { }
) {
    contract {
        callsInPlace(intentModifier, InvocationKind.AT_MOST_ONCE)
    }
    try {
        val intent = Intent(this, kClass.java)
        intentModifier(intent)
        this.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, e.localizedMessage, Toast.LENGTH_LONG).show()
    }
}

fun Context.shareApp(text: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).also {
            it.type = "text/plain"
            it.putExtra(Intent.EXTRA_SUBJECT, R.string.app_name)
            it.putExtra(Intent.EXTRA_TEXT, text)
        }
        this.startActivity(Intent.createChooser(intent, "Share ${R.string.app_name}"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, e.localizedMessage, Toast.LENGTH_LONG).show()
    }
}
