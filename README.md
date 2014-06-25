FlowTextView
============

A wrapping TextView for Android

Original project on Google code https://code.google.com/p/android-flowtextview/

![Logo](http://i.imgur.com/iyntFbz.png)


A TextView that extends RelativeLayout. The text will wrap around any child views inside the layout.

This widget has basic support for HTML using Html.fromHtml("< you markup ... />") It will recognise links, bold italic etc. 

How to use
--------

Add to your XML layout with your child views inside it:
```xml
<com.deanwild.flowtext.FlowTextView
	android:id="@+id/ftv"
	android:layout_width="fill_parent"
	android:layout_height="wrap_content" >

		<ImageView
			android:layout_width="wrap_content"
			android:layout_height="wrap_content"
			android:layout_alignParentLeft="true"
			android:layout_alignParentTop="true"
			android:padding="10dip"
			android:src="@drawable/android"/>

		<ImageView
			android:layout_width="wrap_content"
			android:layout_height="wrap_content"
			android:layout_alignParentRight="true"
			android:layout_marginTop="400dip"
			android:padding="10dip"
			android:src="@drawable/android2"/>
</com.deanwild.flowtext.FlowTextView>
```

Then in your code:
```java
		FlowTextView flowTextView = (FlowTextView) findViewById(R.id.ftv);
        Spanned html = Html.fromHtml("<html>Your html goes here....");
        flowTextView.setText(html);
```

Download
--------

Will be publishing to maven shortly. For now you will need to check out and build manually.


License
-------

    Copyright 2014 Dean Wild

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.


History
-------

I needed to have a text wrap around images and other views in an Android App but I was surprised and frustrated to discover that there was no way to do this natively.

The general consensus was that the only practical way to achieve this was to use WebViews.

Using webviews is a bit bloaty and overkill to achieve this. We lose the direct control and performance of using native widgets.

So I developed a native widget which extends RelativeLayout (I figured this was more versatile than LinearLayout) which is capable of painting text around its child views.

The code is still a little rough and I suspect it could be vastly improved in many areas but it seems to work quite well.
	
TODO
--------

 - Add support for parameters to be supplied directly in XML layout.
 
 - Improve HTML support. For now it can handle basic tags like <b> <i> <br> and <a> but it would be nice to support everything
 
 - Add support for Right to Left text printing.
 
 - Performance - this can always be improved. The larger the text content the slower this beast gets at the moment. Perhaps ART might do the trick though
