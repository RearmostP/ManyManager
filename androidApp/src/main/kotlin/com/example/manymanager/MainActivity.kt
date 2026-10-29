package com.example.manymanager

import android.app.Dialog
import android.content.res.Configuration
import android.os.Parcelable
import android.util.SparseArray
import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.drawable.BitmapDrawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.view.doOnPreDraw
import androidx.core.view.doOnAttach
import androidx.lifecycle.ViewModelProvider
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.min

/** XML-only visual prototype. All amounts and expense rows are static sample data. */
class MainActivity : AppCompatActivity() {
    private data class PageVisit(val page: Int, val tab: Int, val scrollY: Int)
    private val pageHistory = mutableListOf<PageVisit>()
    private val pageBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            val previous = pageHistory.removeLastOrNull() ?: PageVisit(0, 0, 0)
            selectPage(previous.page, animate = true, scrollY = previous.scrollY)
            selectTab(previous.tab)
        }
    }
    private var page = 0
    private var tab = 0
    private var sampleDate = LocalDate.now()
    private companion object {
        // Preview preference survives Activity recreation without storing app data.
        var dark = false
    }
    private val appearanceTransition by lazy {
        ViewModelProvider(this)[AppearanceTransitionState::class.java]
    }
    private var transitionOverlay: ImageView? = null
    private var pageTransition: ValueAnimator? = null
    private var appearanceChanging = false
    private var appearanceTransitionDuration = 500L
    private var contentReady = false
    private var renderedConfiguration: Configuration? = null
    private var activeDialog: Dialog? = null
    private val expanded = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        dark = savedInstanceState?.getBoolean("dark", dark) ?: dark
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
        if (!appearanceChanging) {
            val languageChanged = previous != null && previous.locales != newConfig.locales
            prepareAppearanceTransition(if (languageChanged) 250L else 500L)
        }
        contentReady = false
        super.onConfigurationChanged(newConfig)
        // Reload all XML resources in the existing window: no blank Activity surface.
        renderContent(state)
    }

    private fun renderContent(savedInstanceState: Bundle?) {
        dismissActiveDialogImmediately()
        sampleDate = savedInstanceState?.takeIf { it.containsKey("sampleDate") }
            ?.getLong("sampleDate")?.let(LocalDate::ofEpochDay) ?: sampleDate
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
        val month = sampleDate.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        findViewById<TextView>(R.id.monthLabel).text = month
        findViewById<TextView>(R.id.homeMonth).text = month
        findViewById<TextView>(R.id.sampleDayLabel).text =
            sampleDate.withDayOfMonth(1).format(DateTimeFormatter.ofPattern("d MMMM", locale))
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
        pageTransition?.cancel()
        appearanceTransitionDuration = durationMillis
        appearanceChanging = true
        if (!ValueAnimator.areAnimatorsEnabled()) return
        val root = findViewById<View>(R.id.windowRoot)
        if (root.width == 0 || root.height == 0) return
        // Half resolution keeps the temporary frame small, even on high-density devices.
        val frame = Bitmap.createBitmap(
            (root.width / 2).coerceAtLeast(1),
            (root.height / 2).coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(frame)
        canvas.scale(frame.width.toFloat() / root.width, frame.height.toFloat() / root.height)
        root.draw(canvas)
        appearanceTransition.snapshot = frame
        window.setWindowAnimations(0)
    }

    private fun finishAppearanceTransition(savedInstanceState: Bundle?) {
        val parent = findViewById<ViewGroup>(android.R.id.content)
        val frame = appearanceTransition.snapshot
        appearanceTransition.snapshot = null
        val overlay = frame?.let {
            window.setWindowAnimations(0)
            ImageView(this).apply {
                setImageBitmap(it)
                scaleType = ImageView.ScaleType.FIT_XY
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                isClickable = true
                parent.addView(this, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                ))
            }
        }
        transitionOverlay = overlay
        parent.doOnPreDraw {
            savedInstanceState?.getSparseParcelableArray<Parcelable>("viewHierarchy")?.let {
                findViewById<View>(R.id.windowRoot).restoreHierarchyState(it)
            }
            if (savedInstanceState?.containsKey("pageScrollY") == true) {
                findViewById<ScrollView>(R.id.pageScroll).scrollTo(
                    0, savedInstanceState.getInt("pageScrollY")
                )
            }
            overlay?.animate()
                ?.alpha(0f)
                ?.setDuration(if (ValueAnimator.areAnimatorsEnabled()) appearanceTransitionDuration else 0L)
                ?.setInterpolator(AccelerateDecelerateInterpolator())
                ?.withEndAction {
                    parent.removeView(overlay)
                    overlay.setImageDrawable(null)
                    transitionOverlay = null
                    appearanceChanging = false
                }
                ?.start()
            if (overlay == null) appearanceChanging = false
        }
    }

    private fun navigateToPage(index: Int) {
        if (index == page) return
        if (index == 0) {
            pageHistory.clear()
        } else {
            pageHistory.add(PageVisit(page, tab, findViewById<ScrollView>(R.id.pageScroll).scrollY))
        }
        selectPage(index, animate = true)
    }

    private fun selectPage(index: Int, animate: Boolean = false, scrollY: Int = 0) {
        pageTransition?.cancel()
        val scroll = findViewById<ScrollView>(R.id.pageScroll)
        val transitionDuration = if (page == 1) 200L else 180L
        val previousFrame = if (animate && page != index &&
            ValueAnimator.areAnimatorsEnabled() && scroll.width > 0 && scroll.height > 0
        ) {
            val bitmap = Bitmap.createBitmap(
                (scroll.width / 2).coerceAtLeast(1),
                (scroll.height / 2).coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            Canvas(bitmap).apply {
                scale(bitmap.width.toFloat() / scroll.width, bitmap.height.toFloat() / scroll.height)
                drawColor(getColor(R.color.background))
                scroll.draw(this)
            }
            BitmapDrawable(resources, bitmap).apply {
                setBounds(0, 0, scroll.width, scroll.height)
                scroll.overlay.add(this)
            }
        } else null

        page = index
        pageBackCallback.isEnabled = index != 0
        listOf(R.id.homePage, R.id.summaryPage, R.id.settingsPage).forEachIndexed { i, id ->
            findViewById<View>(id).visibility = if (i == index) View.VISIBLE else View.GONE
        }
        // Keep the old frame visible until the destination has been laid out and scrolled.
        val animator = previousFrame?.let { frame ->
            ValueAnimator.ofInt(255, 0).apply {
                duration = transitionDuration
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { frame.alpha = it.animatedValue as Int }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        scroll.overlay.remove(frame)
                        if (pageTransition === animation) pageTransition = null
                    }
                })
            }
        }
        pageTransition = animator
        animator?.start()
        scroll.doOnPreDraw {
            if (page == index && pageTransition === animator) {
                scroll.scrollTo(0, scrollY)
            }
        }
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
        if (activeLanguage() == tag || appearanceChanging || transitionOverlay != null) return
        prepareAppearanceTransition(durationMillis = 250L)
        page = 2
        expanded.add(R.id.languageDetails)
        // Updates the existing window and persists through AndroidX (API 30–32) or
        // the platform LocaleManager (API 33+), including system-settings changes.
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    private fun changeTheme(value: Boolean) {
        if (dark != value && !appearanceChanging && transitionOverlay == null) {
            prepareAppearanceTransition()
            dark = value
            delegate.localNightMode = if (value) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
        }
    }

    private fun showMenu() {
        val dialog = sideMenu()
        listOf(R.id.nav0, R.id.nav1, R.id.nav2).forEachIndexed { index, id ->
            dialog.findViewById<View>(id).setOnClickListener {
                navigateToPage(index)
                dialog.dismiss()
            }
        }
    }

    private fun sideMenu(): Dialog {
        dismissActiveDialogImmediately()
        val dialog = SideMenuDialog(this)
        val button = findViewById<View>(R.id.menuButton)
        val position = IntArray(2)
        button.getLocationOnScreen(position)
        val bounds = window.windowManager.currentWindowMetrics.bounds
        val fromRight = position[0] + button.width / 2 > bounds.centerX()
        val menuWidth = min(dp(320), (bounds.width() * 0.85f).toInt())
        val content = layoutInflater.inflate(R.layout.dialog_menu, null)
        val panel = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundResource(if (fromRight) R.drawable.menu_from_right else R.drawable.menu_from_left)
            clipToOutline = true
            addView(content)
        }
        val host = FrameLayout(this).apply {
            layoutDirection = resources.configuration.layoutDirection
            addView(panel, FrameLayout.LayoutParams(
                menuWidth,
                ViewGroup.LayoutParams.MATCH_PARENT,
                if (fromRight) Gravity.RIGHT else Gravity.LEFT
            ))
            setOnClickListener { dialog.dismiss() }
        }
        content.isClickable = true
        content.findViewById<View>(R.id.closeDialog).setOnClickListener { dialog.dismiss() }
        host.setOnApplyWindowInsetsListener { view, insets ->
            val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            WindowInsets.CONSUMED
        }
        dialog.setContentView(host)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply {
                dimAmount = 0.38f
                windowAnimations = 0
            }
            setDecorFitsSystemWindows(false)
        }
        dialog.panelView = panel
        dialog.opensFromRight = fromRight
        panel.translationX = if (fromRight) menuWidth.toFloat() else -menuWidth.toFloat()
        dialog.show()
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        host.requestApplyInsets()
        panel.animate().translationX(0f).setDuration(280).start()
        activeDialog = dialog
        return dialog
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
        val dialog = sheet(R.layout.dialog_expense, true)
        // Fields illustrate the form; there is deliberately no save action or data storage.
        dialog.findViewById<TextView>(R.id.expenseDate).text =
            sampleDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(resources.configuration.locales[0]))
    }

    private fun enableExpenseDrag(dialog: Dialog, content: View) {
        val handle = content.findViewById<View>(R.id.expenseDragHandle) ?: return
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var startY = 0f
        var dragging = false
        handle.setOnTouchListener { view, event ->
            if ((dialog as? ExpenseSheetDialog)?.isDismissing == true) return@setOnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    content.animate().cancel()
                    startY = event.rawY - content.translationY
                    dragging = false
                    view.parent.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val distance = (event.rawY - startY).coerceAtLeast(0f)
                    if (distance > slop) dragging = true
                    if (dragging) content.translationY = distance
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    if (event.actionMasked == MotionEvent.ACTION_UP &&
                        dragging && content.translationY >= dp(64)
                    ) {
                        dialog.dismiss()
                    } else {
                        content.animate().translationY(0f).setDuration(180).start()
                        if (!dragging && event.actionMasked == MotionEvent.ACTION_UP) {
                            view.performClick()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun sheet(layout: Int, tall: Boolean): Dialog {
        dismissActiveDialogImmediately()
        val dialog = ExpenseSheetDialog(this)
        val content = layoutInflater.inflate(layout, null)
        // The transparent window fills the screen; only the sheet wraps its content.
        // FrameLayout constrains long forms to the available height and anchors them below.
        val host = FrameLayout(this).apply {
            layoutDirection = resources.configuration.layoutDirection
            addView(content, FrameLayout.LayoutParams(
                min(resources.displayMetrics.widthPixels, dp(440)),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ))
            setOnClickListener { dialog.dismiss() }
        }
        content.isClickable = true
        dialog.sheetView = content
        enableExpenseDrag(dialog, content)
        content.findViewById<View>(R.id.closeDialog).setOnClickListener { dialog.dismiss() }
        host.setOnApplyWindowInsetsListener { view, insets ->
            val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            val bottom = maxOf(safe.bottom, ime.bottom)
            val available = (window.windowManager.currentWindowMetrics.bounds.height() -
                safe.top - bottom).coerceAtLeast(0)
            val topGap = if (tall) (available * 0.08f).toInt() else 0
            view.setPadding(safe.left, safe.top + topGap, safe.right, bottom)
            WindowInsets.CONSUMED
        }
        dialog.setContentView(host)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply {
                dimAmount = 0.38f
                windowAnimations = 0
            }
            setDecorFitsSystemWindows(false)
        }
        dialog.show()
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        host.requestApplyInsets()
        activeDialog = dialog
        return dialog
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
        outState.putBoolean("dark", dark)
        outState.putInt("page", page)
        outState.putIntArray("pageHistory", pageHistory.flatMap {
            listOf(it.page, it.tab, it.scrollY)
        }.toIntArray())
        outState.putInt("tab", tab)
        outState.putLong("sampleDate", sampleDate.toEpochDay())
        outState.putIntArray("expanded", expanded.toIntArray())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        saveUiState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        pageTransition?.cancel()
        transitionOverlay?.animate()?.cancel()
        transitionOverlay?.setImageDrawable(null)
        transitionOverlay = null
        dismissActiveDialogImmediately()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
