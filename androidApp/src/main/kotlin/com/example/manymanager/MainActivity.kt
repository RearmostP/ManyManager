package com.example.manymanager

import android.app.Dialog
import android.content.res.Configuration
import android.os.Parcelable
import android.util.SparseArray
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.core.view.doOnAttach
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import java.time.YearMonth
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.min

/** Coordinates the XML screens, navigation, and application appearance. */
class MainActivity : AppCompatActivity() {
    private data class PageVisit(val page: Int, val tab: Int, val scrollY: Int)
    private val pageHistory = mutableListOf<PageVisit>()
    private val pageBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (page == 0 && budgetState.breakdownExpanded) {
                homeBudgetUi?.setExpanded(false)
                return
            }
            val previous = pageHistory.removeLastOrNull() ?: PageVisit(0, 0, 0)
            selectPage(previous.page, animate = true, scrollY = previous.scrollY)
            selectTab(previous.tab)
        }
    }
    private val budgetState by lazy { ViewModelProvider(this)[BudgetViewModel::class.java] }
    private var homeBudgetUi: BudgetHomeUi? = null
    private var budgetManagementUi: BudgetManagementUi? = null
    private var page = 0
    private var tab = 0
    private var currentDate = LocalDate.now()
    private val preferences by lazy { AppearancePreferences(this) }
    private var dark = false
    private val appearanceTransition by lazy { AppearanceTransitionState(window) }
    private var contentReady = false
    private var renderedConfiguration: Configuration? = null
    private var activeDialog: Dialog? = null
    private val expanded = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        dark = preferences.dark
        // AppCompat owns both locale and night-mode configuration; no competing Context override.
        delegate.localNightMode = if (dark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        super.onCreate(savedInstanceState)
        // AppCompat has restored its saved locales by this point. Preserve Hebrew for
        // installations without a selection, regardless of the device language.
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("he"))
        }
        onBackPressedDispatcher.addCallback(this, pageBackCallback)
        renderContent(savedInstanceState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        val previous = renderedConfiguration
        val appearanceChanged = previous == null ||
            previous.locales != newConfig.locales ||
            previous.layoutDirection != newConfig.layoutDirection ||
            (previous.uiMode and Configuration.UI_MODE_NIGHT_MASK) !=
            (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        if (!contentReady || !appearanceChanged) {
            super.onConfigurationChanged(newConfig)
            return
        }
        val state = Bundle().also { saveUiState(it) }
        if (!appearanceTransition.isChanging) {
            val languageChanged = previous != null && previous.locales != newConfig.locales
            prepareAppearanceTransition(if (languageChanged) 250L else 500L)
        }
        contentReady = false
        super.onConfigurationChanged(newConfig)
        // Reload all XML resources in the existing window: no blank Activity surface.
        renderContent(state)
    }

    private fun renderContent(savedInstanceState: Bundle?) {
        homeBudgetUi?.dispose()
        homeBudgetUi = null
        budgetManagementUi = null
        dismissActiveDialogImmediately()
        currentDate = savedInstanceState?.takeIf { it.containsKey("currentDate") }
            ?.getLong("currentDate")?.let(LocalDate::ofEpochDay) ?: currentDate
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
            width = min(window.windowManager.currentWindowMetrics.bounds.width(), dp(440))
        }
        findViewById<View>(R.id.windowRoot).addOnLayoutChangeListener { root, _, _, _, _, _, _, _, _ ->
            val available = (root.width - root.paddingLeft - root.paddingRight).coerceAtLeast(0)
            val target = min(available, dp(440))
            if (target > 0 && container.layoutParams.width != target) {
                container.layoutParams = container.layoutParams.apply { width = target }
            }
        }
        findViewById<View>(R.id.windowRoot).setOnApplyWindowInsetsListener { view, insets ->
            val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, maxOf(safe.bottom, ime.bottom))
            insets
        }

        findViewById<View>(R.id.windowRoot).doOnAttach { it.requestApplyInsets() }

        savedInstanceState?.getIntArray("pageHistory")?.let { visits ->
            pageHistory.clear()
            visits.toList().chunked(3).filter { it.size == 3 }.forEach {
                pageHistory.add(PageVisit(it[0], it[1], it[2]))
            }
        }
        page = savedInstanceState?.getInt("page") ?: 0
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
                findViewById<View>(arrows[index]).rotation = if (open) {
                    if (resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) -90f else 90f
                } else 0f
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

        bindLanguages()
        val locale = resources.configuration.locales[0]
        val month = currentDate.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        findViewById<TextView>(R.id.monthLabel).text = month
        findViewById<TextView>(R.id.homeMonth).text = month
        bindEmptyState()
        budgetState.homeMonth = YearMonth.from(currentDate)
        homeBudgetUi = BudgetHomeUi(findViewById(R.id.homePage), budgetState) {
            pageBackCallback.isEnabled = page != 0 || budgetState.breakdownExpanded
        }
        budgetManagementUi = BudgetManagementUi(
            findViewById(R.id.budgetPage), budgetState, ::openPaymentSources
        ) { homeBudgetUi?.refresh() }
        listOf(R.id.previousMonth, R.id.nextMonth).forEach { id ->
            findViewById<View>(id).apply {
                isEnabled = false
                alpha = 0.45f
            }
        }
        selectPage(page)
        selectTab(tab)
        if (savedInstanceState?.getBoolean("expenseOpen") == true) {
            showExpense()
            savedInstanceState?.getBundle("expenseDialogState")?.let { state ->
                activeDialog?.onRestoreInstanceState(state)
            }
        }
        if (savedInstanceState?.containsKey("pageScrollY") != true &&
            page == 2 && R.id.languageDetails in expanded
        ) {
            findViewById<View>(R.id.languageDetails).post {
                val section = findViewById<View>(R.id.languageDetails)
                section.requestRectangleOnScreen(Rect(0, 0, section.width, section.height), false)
            }
        }
        renderedConfiguration = Configuration(resources.configuration)
        contentReady = true
        finishAppearanceTransition(savedInstanceState)
    }

    private fun prepareAppearanceTransition(durationMillis: Long = 500L) {
        appearanceTransition.prepare(findViewById(R.id.windowRoot), durationMillis)
    }

    private fun finishAppearanceTransition(savedInstanceState: Bundle?) {
        appearanceTransition.finish(findViewById(android.R.id.content)) {
            savedInstanceState?.getSparseParcelableArray<Parcelable>("viewHierarchy")?.let {
                findViewById<View>(R.id.windowRoot).restoreHierarchyState(it)
            }
            if (savedInstanceState?.containsKey("pageScrollY") == true) {
                findViewById<ScrollView>(R.id.pageScroll).scrollTo(
                    0, savedInstanceState.getInt("pageScrollY")
                )
            }
        }
    }

    private fun bindEmptyState() {
        val numbers = java.text.NumberFormat.getIntegerInstance(resources.configuration.locales[0])
        val total = numbers.format(ExpenseUiData.monthlyTotal)
        findViewById<TextView>(R.id.homeTotal).text = total
        findViewById<TextView>(R.id.summaryTotal).text = total
        findViewById<TextView>(R.id.summaryCount).text = getString(
            R.string.monthly_expense_count, numbers.format(ExpenseUiData.expenseCount)
        )
        findViewById<View>(R.id.settingsCategoriesEmpty).visibility =
            if (ExpenseUiData.categories.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.settingsPaymentMethodsEmpty).visibility =
            if (ExpenseUiData.paymentMethods.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun navigateToPage(index: Int) {
        if (index == page) return
        if (index == 3) budgetState.managementMonth = budgetState.homeMonth
        if (index == 0) {
            pageHistory.clear()
        } else {
            pageHistory.add(PageVisit(page, tab, findViewById<ScrollView>(R.id.pageScroll).scrollY))
        }
        selectPage(index, animate = true)
    }

    private fun selectPage(index: Int, animate: Boolean = false, scrollY: Int = 0) {
        val scroll = findViewById<ScrollView>(R.id.pageScroll)
        appearanceTransition.changePage(
            scroll = scroll,
            animate = animate && page != index,
            durationMillis = if (page == 1) 200L else 180L,
            background = getColor(R.color.background),
            updatePage = {
                if (page == 0 && index != 0) homeBudgetUi?.setExpanded(false, animate = false)
                page = index
                pageBackCallback.isEnabled = index != 0 || budgetState.breakdownExpanded
                if (index == 0) homeBudgetUi?.refresh()
                if (index == 3) budgetManagementUi?.render()
                listOf(R.id.homePage, R.id.summaryPage, R.id.settingsPage, R.id.budgetPage).forEachIndexed { i, id ->
                    findViewById<View>(id).visibility = if (i == index) View.VISIBLE else View.GONE
                }
            },
            restoreScroll = { scroll.scrollTo(0, scrollY) }
        )
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

    private fun activeLanguage(): String =
        AppCompatDelegate.getApplicationLocales()[0]?.language?.let {
            if (it == "iw") "he" else it
        } ?: "he"

    private fun bindLanguages() {
        val choices = listOf(
            Triple(R.id.languageHe, "he", R.string.language_he),
            Triple(R.id.languageEn, "en", R.string.language_en),
            Triple(R.id.languageRu, "ru", R.string.language_ru)
        )
        val active = activeLanguage()
        choices.forEach { (id, tag, label) ->
            val name = getString(label)
            val selected = active == tag
            findViewById<TextView>(id).apply {
                text = if (selected) getString(R.string.language_selected, name) else name
                contentDescription = if (selected) {
                    getString(R.string.language_selected_description, name)
                } else name
                isSelected = selected
                setBackgroundResource(if (selected) R.drawable.chip_active else R.drawable.chip)
                setTextColor(getColor(if (selected) R.color.blue else R.color.text))
                setOnClickListener { selectLanguage(tag) }
            }
            if (selected) findViewById<TextView>(R.id.languageLabel).text = name
        }
    }

    private fun selectLanguage(tag: String) {
        if (activeLanguage() == tag || appearanceTransition.isChanging) return
        prepareAppearanceTransition(durationMillis = 250L)
        page = 2
        expanded.add(R.id.languageDetails)
        // Updates the existing window and persists through AndroidX (API 30–32) or
        // the platform LocaleManager (API 33+), including system-settings changes.
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    private fun changeTheme(value: Boolean) {
        if (dark != value && !appearanceTransition.isChanging) {
            prepareAppearanceTransition()
            preferences.dark = value
            dark = value
            delegate.localNightMode = if (value) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
        }
    }

    private fun openPaymentSources() {
        navigateToPage(2)
        if (R.id.sourcesDetails !in expanded) findViewById<View>(R.id.sourcesToggle).performClick()
        findViewById<View>(R.id.sourcesDetails).post {
            val section = findViewById<View>(R.id.sourcesDetails)
            section.requestRectangleOnScreen(Rect(0, 0, section.width, section.height), false)
        }
    }

    private fun showMenu() {
        dismissActiveDialogImmediately()
        val dialog = SideMenuDialog(this, findViewById(R.id.menuButton), ::navigateToPage)
        activeDialog = dialog
        dialog.setOnDismissListener { if (activeDialog === dialog) activeDialog = null }
        dialog.show()
    }

    private fun dismissActiveDialogImmediately() {
        val dialog = activeDialog
        when (dialog) {
            is ExpenseSheetDialog -> dialog.dismissImmediately()
            is SideMenuDialog -> dialog.dismissImmediately()
            else -> dialog?.dismiss()
        }
        activeDialog = null
    }

    private fun showExpense() {
        dismissActiveDialogImmediately()
        val dialog = ExpenseSheetDialog(this, currentDate)
        activeDialog = dialog
        dialog.setOnDismissListener { if (activeDialog === dialog) activeDialog = null }
        dialog.show()
    }

    private fun saveUiState(outState: Bundle) {
        val hierarchy = SparseArray<Parcelable>()
        findViewById<View>(R.id.windowRoot).saveHierarchyState(hierarchy)
        outState.putSparseParcelableArray("viewHierarchy", hierarchy)
        val expenseOpen = activeDialog?.let { dialog ->
            dialog.isShowing && dialog.findViewById<View>(R.id.amount) != null
        } == true
        outState.putBoolean("expenseOpen", expenseOpen)
        if (expenseOpen) outState.putBundle("expenseDialogState", activeDialog?.onSaveInstanceState())
        outState.putInt("pageScrollY", findViewById<ScrollView>(R.id.pageScroll).scrollY)
        outState.putInt("page", page)
        outState.putIntArray("pageHistory", pageHistory.flatMap {
            listOf(it.page, it.tab, it.scrollY)
        }.toIntArray())
        outState.putInt("tab", tab)
        outState.putLong("currentDate", currentDate.toEpochDay())
        outState.putIntArray("expanded", expanded.toIntArray())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        saveUiState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        homeBudgetUi?.dispose()
        homeBudgetUi = null
        budgetManagementUi = null
        appearanceTransition.clear()
        dismissActiveDialogImmediately()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
