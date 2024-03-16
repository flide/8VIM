package inc.flide.vim8.lib.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import inc.flide.vim8.R
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KClass

fun Context.launchUrl(url: String) {
    val intent = Intent().also {
        it.action = Intent.ACTION_VIEW
        it.data = Uri.parse(url)
    }
    try {
        this.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        showToast(R.string.general__no_browser_app_found_for_url, "url" to url)
    }
}

inline fun <T : Any> Context.launchActivity(
    kClass: KClass<T>,
    intentModifier: (Intent) -> Unit = { }
) {
    contract {
        callsInPlace(intentModifier, InvocationKind.EXACTLY_ONCE)
    }
    try {
        val intent = Intent(this, kClass.java)
        intentModifier(intent)
        this.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        showToast(e.localizedMessage ?: "")
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
        showToast(e.localizedMessage ?: "")
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
