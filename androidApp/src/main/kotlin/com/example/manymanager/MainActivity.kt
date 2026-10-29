package com.example.manymanager

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

/** XML-only visual prototype. All amounts and expense rows are static sample data. */
class MainActivity : ComponentActivity() {
    private var page = 1
    private var tab = 0
    private companion object {
        // Preview preference survives Activity recreation without storing app data.
        var dark = false
    }
    private var activeDialog: Dialog? = null
    private val expanded = mutableSetOf<Int>()

    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            setLocale(Locale.forLanguageTag("he"))
            setLayoutDirection(Locale.forLanguageTag("he"))
        }
        // Configure the context before the framework initializes Activity resources.
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContentView(R.layout.activity_main)

        // Keep the reference's 440dp content width on larger displays.
        val container = findViewById<View>(R.id.appContainer)
        container.layoutParams = (container.layoutParams as FrameLayout.LayoutParams).apply {
            width = min(resources.displayMetrics.widthPixels, dp(440))
        }
        findViewById<View>(R.id.windowRoot).setOnApplyWindowInsetsListener { view, insets ->
            val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, maxOf(safe.bottom, ime.bottom))
            insets
        }

        page = savedInstanceState?.getInt("page") ?: 1
        tab = savedInstanceState?.getInt("tab") ?: 0
        savedInstanceState?.getIntArray("expanded")?.forEach { expanded.add(it) }
        findViewById<View>(R.id.menuButton).setOnClickListener { showMenu() }
        findViewById<View>(R.id.addExpense).setOnClickListener { showExpense() }
        listOf(R.id.categoryTab, R.id.sourceTab, R.id.allTab).forEachIndexed { index, id ->
            findViewById<View>(id).setOnClickListener { selectTab(index) }
        }
        val toggles = listOf(
            R.id.categoriesToggle to R.id.categoriesDetails,
            R.id.sourcesToggle to R.id.sourcesDetails,
            R.id.languageToggle to R.id.languageDetails,
            R.id.themeToggle to R.id.themeDetails,
            R.id.accountToggle to R.id.accountDetails,
            R.id.syncToggle to R.id.syncDetails
        )
        val arrows = listOf(R.id.categoriesChevron, R.id.sourcesChevron, R.id.languageChevron,
            R.id.themeChevron, R.id.accountChevron, R.id.syncChevron)
        toggles.forEachIndexed { index, (toggle, details) ->
            fun update() {
                val open = details in expanded
                findViewById<View>(details).visibility = if (open) View.VISIBLE else View.GONE
                findViewById<View>(arrows[index]).rotation = if (open) -90f else 0f
                findViewById<View>(toggle).isSelected = open
            }
            update()
            findViewById<View>(toggle).setOnClickListener {
                if (!expanded.add(details)) expanded.remove(details)
                update()
            }
        }
        findViewById<View>(R.id.lightTheme).setOnClickListener { changeTheme(false) }
        findViewById<View>(R.id.darkTheme).setOnClickListener { changeTheme(true) }
        findViewById<TextView>(R.id.themeLabel).setText(
            if (dark) R.string.theme_dark else R.string.theme_light
        )
        listOf(R.id.lightTheme, R.id.darkTheme).forEachIndexed { index, id ->
            val selected = (index == 1) == dark
            findViewById<TextView>(id).apply {
                setBackgroundResource(if (selected) R.drawable.chip_active else R.drawable.chip)
                setTextColor(getColor(if (selected) R.color.blue else R.color.text))
                isSelected = selected
            }
        }

        val month = LocalDate.now().format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("he")))
        findViewById<TextView>(R.id.monthLabel).text = month
        findViewById<TextView>(R.id.homeMonth).text = month
        selectPage(page)
        selectTab(tab)
    }

    private fun selectPage(index: Int) {
        page = index
        listOf(R.id.homePage, R.id.summaryPage, R.id.settingsPage).forEachIndexed { i, id ->
            findViewById<View>(id).visibility = if (i == index) View.VISIBLE else View.GONE
        }
        findViewById<ScrollView>(R.id.pageScroll).scrollTo(0, 0)
    }

    private fun selectTab(index: Int) {
        tab = index
        listOf(R.id.categoryPanel, R.id.sourcePanel, R.id.allPanel).forEachIndexed { i, id ->
            findViewById<View>(id).visibility = if (i == index) View.VISIBLE else View.GONE
        }
        listOf(R.id.categoryTab, R.id.sourceTab, R.id.allTab).forEachIndexed { i, id ->
            findViewById<TextView>(id).apply {
                isSelected = i == index
                setTextColor(getColor(if (isSelected) R.color.blue else R.color.muted))
                setBackgroundResource(if (isSelected) R.drawable.tab_selected else R.drawable.tab_idle)
                setTypeface(null, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            }
        }
    }

    private fun changeTheme(value: Boolean) {
        if (dark != value) {
            dark = value
            recreate()
        }
    }

    private fun showMenu() {
        val dialog = sheet(R.layout.dialog_menu, false)
        listOf(R.id.nav0, R.id.nav1, R.id.nav2).forEachIndexed { index, id ->
            dialog.findViewById<View>(id).setOnClickListener {
                selectPage(index)
                dialog.dismiss()
            }
        }
    }

    private fun showExpense() {
        val dialog = sheet(R.layout.dialog_expense, true)
        // Fields illustrate the form; there is deliberately no save action or data storage.
        dialog.findViewById<TextView>(R.id.expenseDate).text =
            LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }

    private fun sheet(layout: Int, tall: Boolean): Dialog {
        activeDialog?.dismiss()
        val dialog = Dialog(this)
        dialog.setContentView(layout)
        dialog.findViewById<View>(R.id.closeDialog).setOnClickListener { dialog.dismiss() }
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.38f }
            setDecorFitsSystemWindows(false)
            decorView.setOnApplyWindowInsetsListener { view, insets ->
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                view.setPadding(safe.left, safe.top, safe.right, maxOf(safe.bottom, ime.bottom))
                insets
            }
        }
        dialog.show()
        dialog.window?.setLayout(
            min(resources.displayMetrics.widthPixels, dp(440)),
            if (tall) (resources.displayMetrics.heightPixels * 0.92).toInt() else ViewGroup.LayoutParams.WRAP_CONTENT
        )
        activeDialog = dialog
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("page", page)
        outState.putInt("tab", tab)
        outState.putIntArray("expanded", expanded.toIntArray())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        activeDialog?.dismiss()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
