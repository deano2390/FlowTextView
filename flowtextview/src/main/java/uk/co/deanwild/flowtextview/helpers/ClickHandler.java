package uk.co.deanwild.flowtextview.helpers;

import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import uk.co.deanwild.flowtextview.listeners.OnLinkClickListener;
import uk.co.deanwild.flowtextview.models.HtmlLink;

/**
 * Created by Dean on 24/06/2014.
 */
public class ClickHandler implements View.OnTouchListener{

    private final SpanParser mSpanParser;
    private OnLinkClickListener mOnLinkClickListener;

    private double distance = 0;
    private float x1,y1,x2,y2 = 0f;

    public ClickHandler(SpanParser spanParser) {
        this.mSpanParser = spanParser;
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {

        int event_code = event.getAction();

        if(event_code == MotionEvent.ACTION_DOWN){
            distance = 0;
            x1 = event.getX();
            y1 = event.getY();
        }

        if(event_code == MotionEvent.ACTION_MOVE){
            x2 = event.getX();
            y2 = event.getY();
            distance = getPointDistance(x1, y1, x2, y2);
        }

        if(distance < 10) { // my random guess at an acceptable drift distance to regard this as a click
            if (event_code == MotionEvent.ACTION_UP) {
                // if the event is an "up" and we havn't moved far since the "down", then it's a click
                return onClick(event.getX(), event.getY()); // process the click and say whether we consumed it
            }
            return true;
        }

        return false;
    }

    private boolean onClick(float x, float y){

        List<HtmlLink> links = mSpanParser.getLinks();

        for (HtmlLink link : links) {
            float tlX = link.xOffset;
            float tlY = link.yOffset;
            float brX = link.xOffset + link.width;
            float brY = link.yOffset + link.height;

            if(x > tlX && x < brX){
                if(y > tlY && y < brY){
                    // collision
                    onLinkClick(link.url);
                    return true; // the click was consumed
                }
            }
        }

        return false;
    }

    private void onLinkClick(String url){
        if(mOnLinkClickListener!=null) mOnLinkClickListener.onLinkClick(url);
    }

    private static double getPointDistance(float x1, float y1, float x2, float y2){
        return Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1- y2, 2));
    }

    public OnLinkClickListener getOnLinkClickListener() {
        return mOnLinkClickListener;
    }

    public void setOnLinkClickListener(OnLinkClickListener mOnLinkClickListener) {
        this.mOnLinkClickListener = mOnLinkClickListener;
    }
}
