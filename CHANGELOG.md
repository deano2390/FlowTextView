# Changelog

## 3.0.0

A rewrite in Kotlin on top of the platform's `StaticLayout`. The view still extends
`RelativeLayout` and keeps its package name.

### Added

- Any `Spanned` text renders as it would in a `TextView`: colours, sizes, underline,
  strikethrough, superscript, image spans and so on, not only bold, italic and links.
- Right-to-left text. Children aligned to start/end mirror with the layout direction.
- Justification (API 26+), hyphenation and line break strategies.
- XML attributes: `android:text`, `textAppearance`, `textSize`, `textColor`, `textColorLink`,
  `textColorHighlight`, `fontFamily`, `textStyle`, `letterSpacing`, `lineSpacingExtra`,
  `lineSpacingMultiplier`, `includeFontPadding`, `breakStrategy`, `hyphenationFrequency` and
  `justificationMode`. The standard `textAlignment` and `textDirection` attributes are respected.
  A `flowTextViewStyle` theme attribute sets a default style.
- Properties to match: `textColors`, `linkTextColor(s)`, `highlightColor`, `letterSpacing`,
  `includeFontPadding`, `breakStrategy`, `hyphenationFrequency`, `justificationMode`,
  `setTextSize(unit, size)`, `setLineSpacing(add, mult)` and `setTypeface(typeface, style)`.
- Any `ClickableSpan` is clickable, and the link is highlighted while pressed.
- Accessibility: the text is exposed to TalkBack, and each link is an accessible virtual view.
- Published with sources and javadoc.

### Fixed

- Links did nothing: the touch handler had been disabled because it swallowed every touch. Links now
  work, and touches elsewhere pass through.
- Styling drifted onto the wrong characters after line breaks in the text.
- Characters were dropped: the last character of a span at the end of the text, and lines of a
  single character.
- The app could hang (an infinite loop) when no character fitted beside a child.
- Bold text inside a link, and other overlapping styles, overwrote each other.
- Lines were broken using unstyled widths, so styled text could overflow.
- With three or more children, text could be placed over one of them.
- Left, right and bottom padding were ignored.
- The first measurement used a fixed 100px height, corrected a frame later from `onDraw`. Height is
  now correct on the first layout pass.
- Text styling was lost for `SpannedString` (only `Spannable` was recognised).

### Performance

2.x laid out all of the text, allocating as it went, on every frame it drew, and requested a new
layout from `onDraw`. 3.0 lays the text out only when the text, its styling, the size or the
children change, and drawing reuses that layout.

## Migrating from 2.x

1. Update the dependency to `com.github.deano2390:FlowTextView:3.0.0` and set `minSdk` to 23 or
   higher.
2. Fix the deprecation warnings. The deprecated methods still work:

   | 2.x | 3.0 |
   |---|---|
   | `getTextsize()` | `getTextSize()` / `textSize` |
   | `getColor()`, `setColor(int)` | `getTextColor()`, `setTextColor(int)` / `textColor` |
   | `getTypeFace()` | `getTypeface()` / `typeface` |
   | `setTextPaint(paint)` | Configure `getTextPaint()`, or use `textSize`, `textColor` and `typeface`. This now copies the paint into the view's own paint. |
   | `getLinkPaint()`, `setLinkPaint(paint)` | `linkTextColor`. Links are styled by their spans, so only the colour is applied. |

3. Remove calls to `setPageHeight()` and the `"hideable"` tag on the last child. This undocumented
   feature has been removed.
4. Replace any use of the `helpers` and `models` packages. These were implementation details and
   have been removed.
5. Check the default text appearance. 2.x drew 20sp black text unless told otherwise. 3.0 takes its
   defaults from the theme, as `TextView` does, so it follows dark mode. That is typically 14sp in
   `textColorPrimary`, with links in `textColorLink`. To keep the old look, set
   `android:textSize="20sp"` and `android:textColor="@android:color/black"`.

`setTextSize(float)` still takes pixels, as in 2.x. Use `setTextSize(TypedValue.COMPLEX_UNIT_SP, size)`
for scaled pixels.
