package uk.co.deanwild.flowtextview.listeners

/**
 * Receives taps on [android.text.style.URLSpan] links, such as `<a href>` tags from `Html.fromHtml`.
 *
 * When a listener is set it replaces the span's default behaviour of opening the URL. Other
 * [android.text.style.ClickableSpan]s always receive [android.text.style.ClickableSpan.onClick].
 */
public fun interface OnLinkClickListener {
    public fun onLinkClick(url: String)
}
