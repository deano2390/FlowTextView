package com.deanwild.flowtext.models;

import android.graphics.Bitmap;

/**
* Created by Dean on 24/06/2014.
*/
public class BitmapSpec {

    public BitmapSpec(Bitmap bitmap, int xOffset, int yOffset,
                      int mPadding) {
        super();
        this.bitmap = bitmap;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.mPadding = mPadding;
    }
    public Bitmap bitmap;
    public int xOffset;
    public int yOffset;
    public int mPadding = 10;
}
