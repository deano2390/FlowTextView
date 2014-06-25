FlowTextView
============

Wrapping TextView I wrote a while ago, original project on Google code https://code.google.com/p/android-flowtextview/

A TextView which extends RelativeLayout. The text will wrap around any child views inside the layout.

This widget has basic support for HTML using Html.fromHtml("< you markup ... />") It will recognise links, bold italic etc. 

History: I needed to have a text wrap around images and other views in an Android App but I was surprised and frustrated to discover that there was no way to do this natively.

The general consensus was that the only practical way to achieve this was to use WebViews.

Using webviews is a bit bloaty and overkill to achieve this. We lose the direct control and performance of using native widgets.

I developed a native widget which extends RelativeLayout (I figured this was more versatile than LinearLayout) which is capable of painting text around its child views.

The code is still a little rough and I suspect it could be vastly improved in many areas but it seems to work quite well.
