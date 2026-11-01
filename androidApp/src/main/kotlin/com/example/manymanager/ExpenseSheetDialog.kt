package com.example.manymanager

import android.animation.ValueAnimator
import android.app.Dialog
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.EditText
import android.widget.LinearLayout
import android.os.Bundle
import androidx.core.widget.doAfterTextChanged
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.min
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.doOnPreDraw

/** Form values and choices are independent of rendered views and survive configuration changes. */
class ExpenseDraft(
    var amount: String = "",
    var date: String? = null,
    var note: String = "",
    var categoryId: String? = null,
    var sourceId: String? = null,
) {
    fun toBundle() = Bundle().apply {
        putString("amount", amount)
        putString("date", date)
        putString("note", note)
        putString("categoryId", categoryId)
        putString("sourceId", sourceId)
    }

    companion object {
        fun fromBundle(bundle: Bundle) = ExpenseDraft(
            bundle.getString("amount").orEmpty(),
            bundle.getString("date"),
            bundle.getString("note").orEmpty(),
            bundle.getString("categoryId"),
            bundle.getString("sourceId"),
        )
    }
}

/** Slides only the expense sheet away, leaving the Activity beneath it in place. */
class ExpenseSheetDialog(
    activity: Activity, date: LocalDate,
    categories: CategoryListState, sources: List<PaymentSource>,
    val draft: ExpenseDraft,
    private val onRetryCategories: () -> Unit = {},
    onAddCategory: () -> Unit,
) : Dialog(activity) {
    private val resources get() = context.resources
    private val host: FrameLayout
    private var sheetView: View? = null
    private var openingDraw: OneShotPreDrawListener? = null
    var isDismissing = false
        private set

    init {
        val dialog = this
        val content = layoutInflater.inflate(R.layout.dialog_expense, null)
        // The transparent window fills the screen; only the sheet wraps its content.
        // FrameLayout constrains long forms to the available height and anchors them below.
        host = FrameLayout(context).apply {
            layoutDirection = resources.configuration.layoutDirection
            addView(content, FrameLayout.LayoutParams(
                min(resources.displayMetrics.widthPixels, dp(440)),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ))
            setOnClickListener { dialog.dismiss() }
        }
        content.isClickable = true
        sheetView = content
        enableDrag(content)
        content.findViewById<View>(R.id.closeDialog).setOnClickListener { dialog.dismiss() }
        host.setOnApplyWindowInsetsListener { view, insets ->
            val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            val bottom = maxOf(safe.bottom, ime.bottom)
            val available = (activity.window.windowManager.currentWindowMetrics.bounds.height() -
                safe.top - bottom).coerceAtLeast(0)
            val topGap = (available * 0.08f).toInt()
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

        if (draft.date == null) {
            draft.date = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(resources.configuration.locales[0]))
        }
        content.findViewById<EditText>(R.id.amount).apply {
            setText(draft.amount)
            doAfterTextChanged { draft.amount = it.toString() }
        }
        content.findViewById<EditText>(R.id.expenseDate).apply {
            setText(draft.date)
            doAfterTextChanged { draft.date = it.toString() }
        }
        content.findViewById<EditText>(R.id.expenseNote).apply {
            setText(draft.note)
            doAfterTextChanged { draft.note = it.toString() }
        }
        // Sources are the same identities shown on Budget Management. Expense saving is not implemented.
        content.findViewById<View>(R.id.saveExpense).isEnabled = false
        content.findViewById<View>(R.id.expensePaymentMethodsEmpty).visibility =
            if (sources.isEmpty()) View.VISIBLE else View.GONE
        renderCategories(categories)
        bindChoices(content.findViewById(R.id.expenseSourceChoices),
            sources.map { it.id to it.name }, true)
        content.findViewById<View>(R.id.expenseNewCategory).setOnClickListener { onAddCategory() }
    }

    /** Refreshes only category choices, leaving every other draft field and its view intact. */
    fun renderCategories(state: CategoryListState) {
        val content = sheetView ?: return
        val status = content.findViewById<TextView>(R.id.expenseCategoriesEmpty)
        val message = state.statusMessage()
        status.visibility = if (message == null) View.GONE else View.VISIBLE
        message?.let { status.setText(it) }
        status.setOnClickListener { onRetryCategories() }
        status.isClickable = state is CategoryListState.Failed
        status.isFocusable = state is CategoryListState.Failed
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        val container = content.findViewById<LinearLayout>(R.id.expenseCategoryChoices)
        container.removeAllViews()
        if (state is CategoryListState.Ready) {
            bindChoices(container, state.categories.map { it.id to it.name }, false)
        }
    }

    fun selectCategory(id: String, state: CategoryListState) {
        draft.categoryId = id
        // The creation result may arrive before the list emission. Keep the ID in that race.
        renderCategories(state)
    }

    private fun bindChoices(
        container: LinearLayout, options: List<Pair<String, String>>, sourceChoices: Boolean
    ) {
        options.forEach { (id, name) ->
            container.addView(TextView(context, null, 0, R.style.Text).apply {
                text = name
                tag = id
                textSize = 14f
                minHeight = dp(48)
                setPadding(dp(14), dp(14), dp(14), dp(14))
                setBackgroundResource(if ((if (sourceChoices) draft.sourceId else draft.categoryId) == id)
                    R.drawable.chip_active else R.drawable.chip)
                isSelected = (if (sourceChoices) draft.sourceId else draft.categoryId) == id
                setTextColor(context.getColor(if (isSelected) R.color.blue else R.color.text))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (sourceChoices) draft.sourceId = id else draft.categoryId = id
                    for (index in 0 until container.childCount) {
                        (container.getChildAt(index) as TextView).apply {
                            isSelected = tag == id
                            setBackgroundResource(if (isSelected) R.drawable.chip_active else R.drawable.chip)
                            setTextColor(context.getColor(if (isSelected) R.color.blue else R.color.text))
                        }
                    }
                }
            })
        }
    }

    private fun enableDrag(content: View) {
        val handle = content.findViewById<View>(R.id.expenseDragHandle) ?: return
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var startY = 0f
        var dragging = false
        handle.setOnTouchListener { view, event ->
            if (isDismissing) return@setOnTouchListener true
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
                        dismiss()
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

    override fun show() {
        val alreadyShowing = isShowing
        super.show()
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        host.requestApplyInsets()
        val sheet = sheetView ?: return
        if (alreadyShowing || !ValueAnimator.areAnimatorsEnabled()) return
        // Start below the viewport, then animate only after layout and insets are ready.
        sheet.translationY = context.resources.displayMetrics.heightPixels.toFloat()
        openingDraw = sheet.doOnPreDraw {
            openingDraw = null
            if (isShowing && !isDismissing) {
                sheet.animate()
                    .translationY(0f)
                    .setDuration(320L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }

    override fun dismiss() {
        if (isDismissing) return
        val sheet = sheetView
        if (!isShowing || sheet == null || !sheet.isAttachedToWindow ||
            !ValueAnimator.areAnimatorsEnabled()
        ) {
            dismissImmediately()
            return
        }
        isDismissing = true
        val parentHeight = (sheet.parent as? View)?.height ?: sheet.height
        // Include the bottom inset, so no part of the sheet remains in the window.
        val offscreen = (parentHeight - sheet.top).toFloat()
        sheet.animate().cancel()
        sheet.animate()
            .translationY(offscreen)
            .setDuration(220L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { dismissImmediately() }
            .start()
    }

    /** Used when the Activity replaces its UI or is destroyed; never leave a window behind. */
    fun dismissImmediately() {
        openingDraw?.removeListener()
        openingDraw = null
        sheetView?.animate()?.cancel()
        super.dismiss()
        sheetView = null
        isDismissing = false
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
