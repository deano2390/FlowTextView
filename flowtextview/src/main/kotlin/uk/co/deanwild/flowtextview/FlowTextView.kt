package uk.co.deanwild.flowtextview

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.Spanned
import android.text.TextDirectionHeuristic
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.RelativeLayout
import androidx.core.view.ViewCompat
import uk.co.deanwild.flowtextview.internal.FlowLayoutConfig
import uk.co.deanwild.flowtextview.internal.FlowLayoutEngine
import uk.co.deanwild.flowtextview.internal.FlowLayoutResult
import uk.co.deanwild.flowtextview.internal.LinkAccessibilityHelper
import uk.co.deanwild.flowtextview.internal.Obstacle
import uk.co.deanwild.flowtextview.listeners.OnLinkClickListener
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A [RelativeLayout] that draws text which flows around its child views.
 *
 * Add children (typically images) exactly as you would to a `RelativeLayout`, then set [text]. Lines
 * of text are shortened to avoid every visible child; a child's left, right and bottom margins are
 * kept clear too, while its top margin only positions it. Where a child leaves no usable gap, the
 * text continues below it.
 *
 * Text is laid out with [android.text.StaticLayout], so any [android.text.Spanned] text works: the
 * result of `Html.fromHtml`, a `SpannableString` with colour, size or style spans, right-to-left
 * scripts and so on.
 *
 * The text layout is computed during measurement and cached; drawing does no layout work.
 */
public open class FlowTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.flowTextViewStyle,
    defStyleRes: Int = 0,
) : RelativeLayout(context, attrs, defStyleAttr, defStyleRes) {

    /**
     * The paint used to measure and draw the text. If you change a property that affects
     * measurement (size, typeface, letter spacing…) directly, call [requestLayout] afterwards.
     */
    public val textPaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        density = resources.displayMetrics.density
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TEXT_SIZE_SP, resources.displayMetrics)
    }

    private var config = FlowLayoutConfig()
    private var textLayout: FlowLayoutResult = FlowLayoutResult.empty(0)
    private var textLayoutKey: LayoutKey? = null

    // requestLayout(), drawableStateChanged() and onRtlPropertiesChanged() can be called from the
    // View constructor, before this class's properties are initialised. These two primitives are
    // the only state those overrides touch until `initialised` is set.
    private var textLayoutValid = false
    private var initialised = false

    /** The text to display. [android.text.Spanned] text keeps its styling. */
    public var text: CharSequence = ""
        set(value) {
            field = value
            onTextLayoutInputsChanged()
            if (initialised) accessibilityHelper.invalidateRoot()
        }

    /** The text size in pixels. Use [setTextSize] with a unit to set it in `sp` or `dp`. */
    public var textSize: Float
        get() = textPaint.textSize
        set(value) {
            if (value == textPaint.textSize) return
            textPaint.textSize = value
            onTextLayoutInputsChanged()
        }

    /** The colours of the text for each drawable state. */
    public var textColors: ColorStateList = defaultTextColors(context)
        set(value) {
            field = value
            updateTextColor()
        }

    /** The text colour for the current drawable state. Setting it replaces [textColors]. */
    public var textColor: Int
        get() = textPaint.color
        set(value) {
            textColors = ColorStateList.valueOf(value)
        }

    /** The typeface for the text, or `null` for the default. */
    public var typeface: Typeface?
        get() = textPaint.typeface
        set(value) {
            if (value == textPaint.typeface) return
            textPaint.typeface = value
            onTextLayoutInputsChanged()
        }

    /** Extra space, in pixels, added to each line. See [setLineSpacing]. */
    public val lineSpacingExtra: Float get() = config.lineSpacingExtra

    /** Factor by which each line's height is multiplied. See [setLineSpacing]. */
    public val lineSpacingMultiplier: Float get() = config.lineSpacingMultiplier

    /** The height, in pixels, of a line of unstyled text including line spacing. */
    public val lineHeight: Int
        get() = (textPaint.getFontMetricsInt(null) * config.lineSpacingMultiplier + config.lineSpacingExtra).roundToInt()

    /** The colours of links for each drawable state, applied through `TextPaint.linkColor`. */
    public var linkTextColors: ColorStateList = ColorStateList.valueOf(DEFAULT_LINK_COLOR)
        set(value) {
            field = value
            updateTextColor()
        }

    /** The link colour for the current drawable state. Setting it replaces [linkTextColors]. */
    public var linkTextColor: Int
        get() = textPaint.linkColor
        set(value) {
            linkTextColors = ColorStateList.valueOf(value)
        }

    /** Letter spacing in ems, as for [android.widget.TextView.setLetterSpacing]. */
    public var letterSpacing: Float
        get() = textPaint.letterSpacing
        set(value) {
            if (value == textPaint.letterSpacing) return
            textPaint.letterSpacing = value
            onTextLayoutInputsChanged()
        }

    /** Whether extra space is left above and below the text for accents and descenders. */
    public var includeFontPadding: Boolean
        get() = config.includeFontPadding
        set(value) = updateConfig { it.copy(includeFontPadding = value) }

    /**
     * One of the `Layout.BREAK_STRATEGY_*` constants. Lines beside a child always use
     * [Layout.BREAK_STRATEGY_SIMPLE], because the platform's other strategies do not honour the
     * shortened line widths.
     */
    public var breakStrategy: Int
        get() = config.breakStrategy
        set(value) = updateConfig { it.copy(breakStrategy = value) }

    /** One of the `Layout.HYPHENATION_FREQUENCY_*` constants. */
    public var hyphenationFrequency: Int
        get() = config.hyphenationFrequency
        set(value) = updateConfig { it.copy(hyphenationFrequency = value) }

    /**
     * One of the `LineBreaker.JUSTIFICATION_MODE_*` constants (`Layout.JUSTIFICATION_MODE_*` from
     * API 35). Justification requires API 26; on older versions this has no effect.
     */
    public var justificationMode: Int
        get() = config.justificationMode
        set(value) = updateConfig { it.copy(justificationMode = value) }

    /**
     * Notified with the URL when a [URLSpan] link is tapped, instead of the span opening it.
     * Other [ClickableSpan]s always receive [ClickableSpan.onClick].
     */
    public var onLinkClickListener: OnLinkClickListener? = null

    /** The colour drawn behind a link while it is pressed. */
    public var highlightColor: Int = defaultHighlightColor(context)
        set(value) {
            field = value
            highlightPaint.color = value
            if (pressedLink != null) invalidate()
        }

    private val accessibilityHelper = LinkAccessibilityHelper(this)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPath = Path()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var pressedLink: ClickableSpan? = null
    private var downX = 0f
    private var downY = 0f

    /** The current text layout. Visible for tests and accessibility. */
    internal val currentTextLayout: FlowLayoutResult get() = textLayout

    init {
        // ViewGroups skip onDraw by default.
        setWillNotDraw(false)
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper)
        if (importantForAccessibility == IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        readAttributes(context, attrs, defStyleAttr, defStyleRes)
        highlightPaint.color = highlightColor
        initialised = true
        updateTextColor()
    }

    /** Sets the text size in the given [unit], one of the [TypedValue] `COMPLEX_UNIT_*` constants. */
    public fun setTextSize(unit: Int, size: Float) {
        textSize = TypedValue.applyDimension(unit, size, resources.displayMetrics)
    }

    /**
     * Sets the typeface and style, as for [android.widget.TextView.setTypeface]. Styles the
     * typeface does not provide are synthesised (fake bold, skewed italic).
     */
    public fun setTypeface(typeface: Typeface?, style: Int) {
        if (style > 0) {
            val styled = if (typeface == null) Typeface.defaultFromStyle(style) else Typeface.create(typeface, style)
            val missing = style and styled.style.inv()
            textPaint.isFakeBoldText = missing and Typeface.BOLD != 0
            textPaint.textSkewX = if (missing and Typeface.ITALIC != 0) -0.25f else 0f
            textPaint.typeface = styled
        } else {
            textPaint.isFakeBoldText = false
            textPaint.textSkewX = 0f
            textPaint.typeface = typeface
        }
        onTextLayoutInputsChanged()
    }

    /** Sets line spacing as for [android.widget.TextView.setLineSpacing]. */
    public fun setLineSpacing(add: Float, mult: Float) {
        updateConfig { it.copy(lineSpacingExtra = add, lineSpacingMultiplier = mult) }
    }

    // region measure, layout and draw

    override fun requestLayout() {
        textLayoutValid = false
        super.requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        // Text fills whatever width it is offered; only an unconstrained width wraps the content.
        val width = when (widthMode) {
            MeasureSpec.UNSPECIFIED -> {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                max(measuredWidth, desiredTextWidth())
            }
            else -> MeasureSpec.getSize(widthMeasureSpec)
        }
        val childWidthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)

        var parentHeightSpec = heightMeasureSpec
        var height: Int
        var pass = 0
        while (true) {
            super.onMeasure(childWidthSpec, parentHeightSpec)
            // RelativeLayout resolves child positions while measuring but only applies them in
            // onLayout. Apply them now so the text can flow around the children and the final
            // height is known in this pass, without a second layout request.
            super.onLayout(false, 0, 0, width, measuredHeight)
            updateTextLayout(width)

            val desired = max(measuredHeight, textLayout.bottom + paddingBottom)
            height = when (heightMode) {
                MeasureSpec.EXACTLY -> heightSize
                MeasureSpec.AT_MOST -> min(desired, heightSize)
                else -> desired
            }
            // Children aligned to the bottom or centre moved when the height changed; re-place
            // them once against the final height.
            if (height == measuredHeight || pass++ > 0 || !hasHeightDependentChildren()) break
            parentHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        }

        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        // Normally a no-op: the layout computed in onMeasure is still valid.
        val heightBefore = textLayout.bottom
        if (updateTextLayout(r - l) && textLayout.bottom != heightBefore) requestLayout()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val link = pressedLink
        val spanned = text as? Spanned
        val linkStart = if (link != null && spanned != null) spanned.getSpanStart(link) else -1
        val linkEnd = if (link != null && spanned != null) spanned.getSpanEnd(link) else -1
        for (segment in textLayout.segments) {
            val start = max(linkStart, segment.start)
            val end = min(linkEnd, segment.end)
            if (linkStart >= 0 && start < end) {
                segment.layout.getSelectionPath(start, end, highlightPath)
                segment.draw(canvas, highlightPath, highlightPaint)
            } else {
                segment.draw(canvas)
            }
        }
    }

    // region links

    // Links are not the view's own click: accessibility users reach them through the virtual view
    // hierarchy instead, so performClick() is deliberately not called for a link tap.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val link = clickableSpanAt(event.x, event.y) ?: return super.onTouchEvent(event)
                downX = event.x
                downY = event.y
                setPressedLink(link)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pressedLink == null) return super.onTouchEvent(event)
                if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) setPressedLink(null)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val link = pressedLink ?: return super.onTouchEvent(event)
                setPressedLink(null)
                if (clickableSpanAt(event.x, event.y) === link) performLinkClick(link)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (pressedLink == null) return super.onTouchEvent(event)
                setPressedLink(null)
                return true
            }
        }
        return pressedLink != null || super.onTouchEvent(event)
    }

    /** Returns the [ClickableSpan] drawn under ([x], [y]) in view coordinates, if any. */
    internal fun clickableSpanAt(x: Float, y: Float): ClickableSpan? {
        val spanned = text as? Spanned ?: return null
        val index = textLayout.characterAt(x, y)
        if (index < 0) return null
        return spanned.getSpans(index, index + 1, ClickableSpan::class.java).firstOrNull()
    }

    internal fun performLinkClick(span: ClickableSpan) {
        val listener = onLinkClickListener
        if (listener != null && span is URLSpan) {
            listener.onLinkClick(span.url)
        } else {
            span.onClick(this)
        }
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        accessibilityHelper.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        accessibilityHelper.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    private fun setPressedLink(link: ClickableSpan?) {
        if (link === pressedLink) return
        pressedLink = link
        invalidate()
    }

    // endregion

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        if (initialised) updateTextColor()
    }

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        if (initialised) onTextLayoutInputsChanged()
    }

    /** Recomputes the text layout if its inputs changed. Returns true if it was recomputed. */
    private fun updateTextLayout(width: Int): Boolean {
        val key = LayoutKey(width, paddingLeft, paddingTop, paddingRight, collectObstacles())
        if (textLayoutValid && key == textLayoutKey) return false

        textLayout = FlowLayoutEngine.layout(
            text = text,
            paint = textPaint,
            config = config.copy(alignment = resolveAlignment(), textDirection = resolveTextDirectionHeuristic()),
            left = paddingLeft,
            top = paddingTop,
            right = width - paddingRight,
            obstacles = key.obstacles,
        )
        textLayoutKey = key
        textLayoutValid = true
        return true
    }

    /** Maps the view's `android:textAlignment` onto the layout alignment. */
    private fun resolveAlignment(): Layout.Alignment = when (textAlignment) {
        View.TEXT_ALIGNMENT_CENTER -> Layout.Alignment.ALIGN_CENTER
        View.TEXT_ALIGNMENT_TEXT_END, View.TEXT_ALIGNMENT_VIEW_END -> Layout.Alignment.ALIGN_OPPOSITE
        else -> Layout.Alignment.ALIGN_NORMAL
    }

    /** Maps the view's `android:textDirection` onto a heuristic, as TextView does. */
    private fun resolveTextDirectionHeuristic(): TextDirectionHeuristic {
        val rtlView = layoutDirection == View.LAYOUT_DIRECTION_RTL
        return when (textDirection) {
            View.TEXT_DIRECTION_ANY_RTL -> TextDirectionHeuristics.ANYRTL_LTR
            View.TEXT_DIRECTION_LTR -> TextDirectionHeuristics.LTR
            View.TEXT_DIRECTION_RTL -> TextDirectionHeuristics.RTL
            View.TEXT_DIRECTION_LOCALE -> TextDirectionHeuristics.LOCALE
            View.TEXT_DIRECTION_FIRST_STRONG_LTR -> TextDirectionHeuristics.FIRSTSTRONG_LTR
            View.TEXT_DIRECTION_FIRST_STRONG_RTL -> TextDirectionHeuristics.FIRSTSTRONG_RTL
            else -> if (rtlView) TextDirectionHeuristics.FIRSTSTRONG_RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR
        }
    }

    private fun collectObstacles(): List<Obstacle> {
        val obstacles = ArrayList<Obstacle>(childCount)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val lp = child.layoutParams as? MarginLayoutParams
            obstacles += Obstacle(
                left = child.left - (lp?.leftMargin ?: 0),
                top = child.top,
                right = child.right + (lp?.rightMargin ?: 0),
                bottom = child.bottom + (lp?.bottomMargin ?: 0),
            )
        }
        return obstacles
    }

    private fun hasHeightDependentChildren(): Boolean = (0 until childCount).any { i ->
        val child = getChildAt(i)
        val lp = child.layoutParams as? LayoutParams ?: return@any false
        val rules = lp.rules
        child.visibility != View.GONE && (
            lp.height == LayoutParams.MATCH_PARENT ||
                rules[ALIGN_PARENT_BOTTOM] != 0 ||
                rules[CENTER_VERTICAL] != 0 ||
                rules[CENTER_IN_PARENT] != 0
            )
    }

    private fun desiredTextWidth(): Int =
        ceil(Layout.getDesiredWidth(text, textPaint)).toInt() + paddingLeft + paddingRight

    // endregion

    private inline fun updateConfig(transform: (FlowLayoutConfig) -> FlowLayoutConfig) {
        val updated = transform(config)
        if (updated == config) return
        config = updated
        onTextLayoutInputsChanged()
    }

    private fun onTextLayoutInputsChanged() {
        requestLayout()
        invalidate()
    }

    private fun updateTextColor() {
        val state = drawableState
        val color = textColors.getColorForState(state, textColors.defaultColor)
        val linkColor = linkTextColors.getColorForState(state, linkTextColors.defaultColor)
        if (color != textPaint.color || linkColor != textPaint.linkColor) {
            textPaint.color = color
            textPaint.linkColor = linkColor
            invalidate()
        }
    }

    // region attributes

    private fun readAttributes(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.FlowTextView, defStyleAttr, defStyleRes)
        try {
            // As in TextView, the text appearance supplies defaults that direct attributes override.
            val appearanceRes = a.getResourceId(R.styleable.FlowTextView_android_textAppearance, 0)
            val style = TextStyle()
            if (appearanceRes != 0) {
                val appearance = context.obtainStyledAttributes(appearanceRes, R.styleable.FlowTextViewAppearance)
                try {
                    style.read(context, appearance, APPEARANCE_INDICES)
                } finally {
                    appearance.recycle()
                }
            }
            style.read(context, a, VIEW_INDICES)
            style.applyTo(this)

            config = config.copy(
                lineSpacingExtra = a.getDimension(R.styleable.FlowTextView_android_lineSpacingExtra, config.lineSpacingExtra),
                lineSpacingMultiplier = a.getFloat(R.styleable.FlowTextView_android_lineSpacingMultiplier, config.lineSpacingMultiplier),
                includeFontPadding = a.getBoolean(R.styleable.FlowTextView_android_includeFontPadding, config.includeFontPadding),
                breakStrategy = a.getInt(R.styleable.FlowTextView_android_breakStrategy, config.breakStrategy),
                hyphenationFrequency = a.getInt(R.styleable.FlowTextView_android_hyphenationFrequency, config.hyphenationFrequency),
                justificationMode = a.getInt(R.styleable.FlowTextView_android_justificationMode, config.justificationMode),
            )
            a.getText(R.styleable.FlowTextView_android_text)?.let { text = it }
        } finally {
            a.recycle()
        }
    }

    // endregion

    private data class LayoutKey(
        val width: Int,
        val paddingLeft: Int,
        val paddingTop: Int,
        val paddingRight: Int,
        val obstacles: List<Obstacle>,
    )

    private companion object {
        const val DEFAULT_TEXT_SIZE_SP = 20f
        const val DEFAULT_LINK_COLOR = Color.BLUE

        const val FALLBACK_HIGHLIGHT_COLOR = 0x6633B5E5

        fun defaultHighlightColor(context: Context): Int {
            val a = context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorHighlight))
            try {
                return a.getColor(0, FALLBACK_HIGHLIGHT_COLOR)
            } finally {
                a.recycle()
            }
        }

        fun defaultTextColors(context: Context): ColorStateList {
            val a = context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
            try {
                return a.getColorStateList(0) ?: ColorStateList.valueOf(Color.BLACK)
            } finally {
                a.recycle()
            }
        }
    }
}
