package uk.co.deanwild.flowtextview

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat

// Parsing of the text styling attributes that FlowTextView shares with its android:textAppearance.

/** Styleable indices of the attributes shared by the view and its text appearance. */
internal class StyleIndices(
    val textSize: Int,
    val textColor: Int,
    val textColorLink: Int,
    val textColorHighlight: Int,
    val fontFamily: Int,
    val textStyle: Int,
    val letterSpacing: Int,
)

internal val VIEW_INDICES = StyleIndices(
    R.styleable.FlowTextView_android_textSize,
    R.styleable.FlowTextView_android_textColor,
    R.styleable.FlowTextView_android_textColorLink,
    R.styleable.FlowTextView_android_textColorHighlight,
    R.styleable.FlowTextView_android_fontFamily,
    R.styleable.FlowTextView_android_textStyle,
    R.styleable.FlowTextView_android_letterSpacing,
)

internal val APPEARANCE_INDICES = StyleIndices(
    R.styleable.FlowTextViewAppearance_android_textSize,
    R.styleable.FlowTextViewAppearance_android_textColor,
    R.styleable.FlowTextViewAppearance_android_textColorLink,
    R.styleable.FlowTextViewAppearance_android_textColorHighlight,
    R.styleable.FlowTextViewAppearance_android_fontFamily,
    R.styleable.FlowTextViewAppearance_android_textStyle,
    R.styleable.FlowTextViewAppearance_android_letterSpacing,
)

/** Text styling gathered from a text appearance and the view's own attributes. */
internal class TextStyle {
    var textSize: Float? = null
    var textColors: ColorStateList? = null
    var linkColors: ColorStateList? = null
    var highlightColor: Int? = null
    var typeface: Typeface? = null
    var fontFamilySet = false
    var textStyle = Typeface.NORMAL
    var letterSpacing: Float? = null

    fun read(context: Context, a: TypedArray, i: StyleIndices) {
        if (a.hasValue(i.textSize)) textSize = a.getDimension(i.textSize, 0f)
        a.getColorStateList(i.textColor)?.let { textColors = it }
        a.getColorStateList(i.textColorLink)?.let { linkColors = it }
        if (a.hasValue(i.textColorHighlight)) highlightColor = a.getColor(i.textColorHighlight, 0)
        if (a.hasValue(i.fontFamily)) {
            typeface = readFontFamily(context, a, i.fontFamily)
            fontFamilySet = true
        }
        if (a.hasValue(i.textStyle)) textStyle = a.getInt(i.textStyle, Typeface.NORMAL)
        if (a.hasValue(i.letterSpacing)) letterSpacing = a.getFloat(i.letterSpacing, 0f)
    }

    fun readTextAppearance(context: Context, appearanceRes: Int) {
        val appearance = context.obtainStyledAttributes(appearanceRes, R.styleable.FlowTextViewAppearance)
        try {
            read(context, appearance, APPEARANCE_INDICES)
        } finally {
            appearance.recycle()
        }
    }

    fun applyTo(view: FlowTextView) {
        textSize?.let { view.textPaint.textSize = it }
        textColors?.let { view.textColors = it }
        linkColors?.let { view.linkTextColors = it }
        highlightColor?.let { view.highlightColor = it }
        letterSpacing?.let { view.textPaint.letterSpacing = it }
        if (fontFamilySet || textStyle != Typeface.NORMAL) view.setTypeface(typeface, textStyle)
    }

    /** A font resource (`@font/…`) or a family name such as `sans-serif-medium`. */
    private fun readFontFamily(context: Context, a: TypedArray, index: Int): Typeface? {
        val resId = a.getResourceId(index, 0)
        if (resId != 0 && a.resources.getResourceTypeName(resId) == "font") {
            try {
                // Resolves XML font families and downloadable fonts on every API level.
                ResourcesCompat.getFont(context, resId)?.let { return it }
            } catch (_: Resources.NotFoundException) {
                // Fall back to treating the value as a family name.
            }
        }
        return a.getString(index)?.let { Typeface.create(it, Typeface.NORMAL) }
    }
}
