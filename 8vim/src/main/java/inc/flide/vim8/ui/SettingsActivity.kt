package inc.flide.vim8.ui

import androidx.constraintlayout.widget.ConstraintLayout
import android.os.Bundle
import inc.flide.vim8.R
import android.view.View.OnTouchListener
import android.content.Intent
import inc.flide.vim8.ui.SettingsActivity
import com.google.android.material.navigation.NavigationView
import androidx.drawerlayout.widget.DrawerLayout
import inc.flide.vim8.ui.SettingsFragment
import android.widget.Toast
import inc.flide.vim8.ui.AboutUsActivity
import androidx.core.view.GravityCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.MaterialDialog.SingleButtonCallback
import com.afollestad.materialdialogs.DialogAction
import androidx.preference.PreferenceFragmentCompat
import android.content.SharedPreferences
import android.app.Activity
import inc.flide.vim8.keyboardActionListners.MainKeypadActionListener
import androidx.preference.SeekBarPreference
import inc.flide.vim8.preferences.SharedPreferenceHelper
import com.afollestad.materialdialogs.MaterialDialog.ListCallbackSingleChoice
import inc.flide.vim8.R.raw
import android.graphics.PointF
import inc.flide.vim8.geometry.Circle
import android.graphics.RectF
import android.graphics.PathMeasure
import inc.flide.vim8.MainInputMethodService
import android.view.View.MeasureSpec
import android.graphics.Typeface
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import android.widget.ImageButton
import inc.flide.vim8.keyboardHelpers.InputMethodViewHelper
import android.inputmethodservice.KeyboardView
import android.inputmethodservice.Keyboard
import inc.flide.vim8.views.ButtonKeypadView
import inc.flide.vim8.keyboardActionListners.ButtonKeypadActionListener
import inc.flide.vim8.geometry.GeometricUtilities
import kotlin.jvm.JvmOverloads
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import org.xmlpull.v1.XmlPullParser
import kotlin.Throws
import org.xmlpull.v1.XmlPullParserException
import inc.flide.vim8.keyboardHelpers.KeyboardDataXmlParser
import android.util.Xml
import inc.flide.vim8.keyboardHelpers.InputMethodServiceHelper
import android.media.AudioManager
import inc.flide.vim8.keyboardActionListners.KeypadActionListener
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener
import android.inputmethodservice.InputMethodService
import inc.flide.vim8.views.mainKeyboard.MainKeyboardView
import inc.flide.vim8.views.NumberKeypadView
import inc.flide.vim8.views.SelectionKeypadView
import inc.flide.vim8.views.SymbolKeypadView
import android.os.IBinder
import android.text.TextUtils
import android.app.Application
import android.net.Uri
import android.os.Handler
import android.provider.Settings
import android.view.*
import android.view.inputmethod.*
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import inc.flide.vim8.BuildConfig
import inc.flide.vim8.structures.*

class SettingsActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private var isKeyboardEnabled = false
    private var pressBackTwice = false
    private var isActivityRestarting = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_page_layout)
        val toolbar = findViewById<Toolbar?>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setBackgroundColor(resources.getColor(R.color.white))
        toolbar.setTitleTextColor(resources.getColor(R.color.black))
        val drawer = findViewById<DrawerLayout?>(R.id.drawer_layout)
        val toggle = ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer.addDrawerListener(toggle)
        toggle.syncState()
        val navigationView = findViewById<NavigationView?>(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)
        supportFragmentManager.beginTransaction()
                .replace(R.id.settings_fragment, SettingsFragment())
                .commit()
    }

    override fun onBackPressed() {
        if (pressBackTwice) {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
            System.exit(0)
        }
        pressBackTwice = true
        Toast.makeText(this@SettingsActivity, "Please press Back again to exit", Toast.LENGTH_SHORT).show()
        Handler().postDelayed({ pressBackTwice = false }, 2000)
    }

    override fun onNavigationItemSelected(item: MenuItem?): Boolean {
        if (item.getItemId() == R.id.share) {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, R.string.app_name)
            var shareMessage = "\nCheck out this awesome keyboard application\n\n"
            shareMessage = """
                ${shareMessage}https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}
                
                """.trimIndent()
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareMessage)
            startActivity(Intent.createChooser(shareIntent, "Share " + R.string.app_name))
        } else if (item.getItemId() == R.id.help) {
            val intent = Intent(Intent.ACTION_VIEW)
            val data = Uri.parse("mailto:flideravi@gmail.com?subject=" + "Feedback")
            intent.data = data
            startActivity(intent)
        } else if (item.getItemId() == R.id.about) {
            val intentAboutUs = Intent(this@SettingsActivity, AboutUsActivity::class.java)
            startActivity(intentAboutUs)
        }
        val drawer = findViewById<DrawerLayout?>(R.id.drawer_layout)
        drawer.closeDrawer(GravityCompat.START)
        return true
    }

    public override fun onRestart() {
        super.onRestart()
        isActivityRestarting = true
    }

    override fun onStart() {
        super.onStart()
        if (!isActivityRestarting) {

            // Ask user to activate the IME while he is using the settings application
            if (Constants.SELF_KEYBOARD_ID != Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)) {
                activateInputMethodDialog()
            }
        }
        isActivityRestarting = false
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledInputMethodList = inputMethodManager.enabledInputMethodList
        isKeyboardEnabled = false
        for (inputMethodInfo in enabledInputMethodList) {
            if (inputMethodInfo.id.compareTo(Constants.SELF_KEYBOARD_ID) == 0) {
                isKeyboardEnabled = true
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Ask user to enable the IME if it is not enabled yet
        if (!isKeyboardEnabled) {
            enableInputMethodDialog()
        }
    }

    private fun enableInputMethodDialog() {
        val enableInputMethodNotificationDialog = MaterialDialog.Builder(this)
                .title(R.string.enable_ime_dialog_title)
                .content(R.string.enable_ime_dialog_content)
                .neutralText(R.string.enable_ime_dialog_neutral_button_text)
                .cancelable(false)
                .canceledOnTouchOutside(false)
                .build()
        enableInputMethodNotificationDialog.builder
                .onNeutral { dialog: MaterialDialog?, which: DialogAction? ->
                    showToast()
                    startActivityForResult(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS), 0)
                    enableInputMethodNotificationDialog.dismiss()
                }
        enableInputMethodNotificationDialog.show()
    }

    fun showToast() {
        val inflater = layoutInflater
        val layout = inflater.inflate(R.layout.custom_toast,
                findViewById(R.id.toast_layout))
        val toast = Toast(applicationContext)
        toast.setGravity(Gravity.BOTTOM or Gravity.FILL_HORIZONTAL, 0, 0)
        toast.duration = Toast.LENGTH_LONG
        toast.view = layout
        toast.show()
    }

    private fun activateInputMethodDialog() {
        val activateInputMethodNotificationDialog = MaterialDialog.Builder(this)
                .title(R.string.activate_ime_dialog_title)
                .content(R.string.activate_ime_dialog_content)
                .positiveText(R.string.activate_ime_dialog_positive_button_text)
                .negativeText(R.string.activate_ime_dialog_negative_button_text)
                .cancelable(false)
                .canceledOnTouchOutside(false)
                .build()
        activateInputMethodNotificationDialog.builder
                .onPositive { dialog: MaterialDialog?, which: DialogAction? ->
                    val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    inputMethodManager.showInputMethodPicker()
                    activateInputMethodNotificationDialog.dismiss()
                }
        activateInputMethodNotificationDialog.show()
    }
}