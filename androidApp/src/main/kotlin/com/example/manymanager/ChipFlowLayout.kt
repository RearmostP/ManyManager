package com.example.manymanager

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import kotlin.math.max

/** Keeps the existing chip spacing while wrapping longer translated labels. */
class ChipFlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {
    private val gap = (8 * resources.displayMetrics.density).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight)
            .coerceAtLeast(0)
        var rowWidth = 0
        var rowHeight = 0
        var totalHeight = paddingTop + paddingBottom
        var widest = 0
        var state = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) continue
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            val spacing = if (rowWidth == 0) 0 else gap
            if (rowWidth > 0 && rowWidth + spacing + child.measuredWidth > available) {
                totalHeight += rowHeight + gap
                widest = max(widest, rowWidth)
                rowWidth = 0
                rowHeight = 0
            }
            if (rowWidth > 0) rowWidth += gap
            rowWidth += child.measuredWidth
            rowHeight = max(rowHeight, child.measuredHeight)
            state = combineMeasuredStates(state, child.measuredState)
        }
        widest = max(widest, rowWidth)
        totalHeight += rowHeight + if (rowHeight > 0) gap else 0
        setMeasuredDimension(
            resolveSizeAndState(widest + paddingLeft + paddingRight, widthMeasureSpec, state),
            resolveSizeAndState(totalHeight, heightMeasureSpec, state shl MEASURED_HEIGHT_STATE_SHIFT)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val available = width - paddingLeft - paddingRight
        val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        var rowWidth = 0
        var rowHeight = 0
        var y = paddingTop
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) continue
            val spacing = if (rowWidth == 0) 0 else gap
            if (rowWidth > 0 && rowWidth + spacing + child.measuredWidth > available) {
                y += rowHeight + gap
                rowWidth = 0
                rowHeight = 0
            }
            if (rowWidth > 0) rowWidth += gap
            val x = if (rtl) width - paddingRight - rowWidth - child.measuredWidth
                else paddingLeft + rowWidth
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
            rowWidth += child.measuredWidth
            rowHeight = max(rowHeight, child.measuredHeight)
        }
    }

    override fun generateDefaultLayoutParams() =
        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
}
