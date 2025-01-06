package inc.flide.vim8.ime.layout

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import arrow.core.Either
import arrow.core.Option
import arrow.core.flatMap
import arrow.core.flatten
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.none
import arrow.core.raise.catch
import arrow.core.right
import arrow.core.some
import inc.flide.vim8.R
import inc.flide.vim8.appPreferenceModel
import inc.flide.vim8.cache
import inc.flide.vim8.datastore.model.PreferenceSerDe
import inc.flide.vim8.ime.layout.models.KeyboardData
import inc.flide.vim8.ime.layout.models.error.ExceptionWrapperError
import inc.flide.vim8.ime.layout.models.error.LayoutError
import inc.flide.vim8.ime.layout.models.info
import inc.flide.vim8.ime.layout.models.yaml.versions.common.name
import inc.flide.vim8.lib.android.tryOrNull
import java.io.InputStream
import java.util.Locale
import org.apache.commons.codec.digest.DigestUtils

private val isoCodes = Locale.getISOLanguages().toSet()

fun embeddedLayouts(
    layoutLoader: LayoutLoader,
    context: Context
): List<Pair<EmbeddedLayout, String>> = (R.raw::class.java.fields)
    .filter { isoCodes.contains(it.name) }
    .flatMap { field ->
        EmbeddedLayout(field.name)
            .let { layout ->
                layout
                    .loadKeyboardData(layoutLoader, context)
                    .getOrNone()
                    .filterNot {
                        it.totalLayers == 0
                    }
                    .map { layout to it.toString() }
            }.toList()
    }.sortedBy { it.second }

interface Layout<T> {
    val path: T
    fun inputStream(context: Context): Either<LayoutError, InputStream>
    fun md5(context: Context): Option<String>
    fun defaultName(context: Context): String
    fun isEmbedded(): Boolean
}

fun safeLoadKeyboardData(layoutLoader: LayoutLoader, context: Context): KeyboardData? {
    val prefs by appPreferenceModel()
    val current = prefs.layout.current.get()
    return current.loadKeyboardData(layoutLoader, context)
        .onLeft {
            if (current is CustomLayout) {
                val historyPref = prefs.layout.custom.history
                val history = LinkedHashSet(historyPref.get())
                history.remove(current.path.toString())
                historyPref.set(history)
            }
            prefs.layout.current.reset()
        }
        .fold(
            { prefs.layout.current.default.loadKeyboardData(layoutLoader, context) },
            { Either.Right(it) }
        )
        .getOrNull()
}

fun <T> Layout<T>.loadKeyboardData(
    layoutLoader: LayoutLoader,
    context: Context
): Either<LayoutError, KeyboardData> = md5(context)
    .toEither { ExceptionWrapperError(Exception("MD5")) }
    .flatMap { md5 ->
        val cache by context.cache()
        cache.load(md5).fold({
            inputStream(context)
                .flatMap {
                    layoutLoader.loadKeyboardData(it)
                }
                .map {
                    KeyboardData.info.name.modify(it) { name ->
                        name.ifEmpty { defaultName(context) }
                    }
                }.onRight { cache.add(md5, it) }
        }, { it.right() })
    }

fun String.toCustomLayout(): CustomLayout {
    return CustomLayout(Uri.parse(this))
}

object LayoutSerDe : PreferenceSerDe<Layout<*>> {
    override fun serialize(editor: SharedPreferences.Editor, key: String, value: Layout<*>) {
        val encodedValue = when (value) {
            is EmbeddedLayout -> "e${value.path}"
            is CustomLayout -> "c${value.path}"
            else -> null
        }

        editor.putString(key, encodedValue)
    }

    override fun deserialize(
        sharedPreferences: SharedPreferences,
        key: String,
        default: Layout<*>
    ): Layout<*> {
        return tryOrNull { sharedPreferences.getString(key, null) }
            ?.let { deserialize(it) } ?: default
    }

    override fun deserialize(value: Any?): Layout<*>? {
        return value?.toString()?.let {
            if (it.length >= 2) {
                val path = it.substring(1)
                when (it[0]) {
                    'e' -> EmbeddedLayout(path)
                    'c' -> CustomLayout(Uri.parse(path))
                    else -> null
                }
            } else {
                null
            }
        }
    }
}

data class EmbeddedLayout(override val path: String) : Layout<String> {
    @SuppressLint("DiscouragedApi")
    override fun inputStream(context: Context): Either<LayoutError, InputStream> {
        val resources = context.resources
        val resourceId =
            resources.getIdentifier(path, "raw", context.packageName)
        return catch({
            resources.openRawResource(resourceId).right()
        }) { e: Throwable -> ExceptionWrapperError(e).left() }
    }

    override fun md5(context: Context): Option<String> = path.some()
    override fun defaultName(context: Context): String {
        val locale = Locale(path)
        return Locale.forLanguageTag(path).getDisplayName(locale)
            .replaceFirstChar { it.titlecase(locale) }
    }

    override fun isEmbedded(): Boolean = true
}

data class CustomLayout(override val path: Uri) : Layout<Uri> {
    @SuppressLint("Recycle")
    override fun inputStream(context: Context): Either<LayoutError, InputStream> = catch({
        context.contentResolver.openInputStream(path)!!.right()
    }) { e: Throwable -> ExceptionWrapperError(e).left() }

    override fun md5(context: Context): Option<String> = inputStream(context)
        .getOrNone()
        .map { DigestUtils.md5Hex(it) }

    override fun defaultName(context: Context): String = Option.catch {
        Option.fromNullable(path.scheme)
            .flatMap {
                when (it) {
                    "file" -> Option.fromNullable(path.lastPathSegment)
                    "content" -> Option.fromNullable(
                        context.contentResolver.query(
                            path,
                            null,
                            null,
                            null,
                            null
                        )?.let { cursor ->
                            var result = ""
                            if (cursor.count != 0) {
                                val columnIndex =
                                    cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                                cursor.moveToFirst()
                                result = cursor.getString(columnIndex)
                            }
                            cursor.close()
                            result
                        }
                    )

                    else -> none()
                }
            }
    }.flatten().getOrElse { "" }

    override fun isEmbedded(): Boolean = false
}
