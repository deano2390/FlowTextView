package uk.co.deanwild.flowtextview;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Html;
import android.text.Spanned;
import android.text.TextPaint;
import android.util.TypedValue;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.GraphicsMode;

import uk.co.deanwild.flowtextview.listeners.OnLinkClickListener;

/**
 * Compiles against FlowTextView exactly as 2.x Java callers did (see the 2.0.5 README and sample),
 * guarding the JVM signatures that existing apps depend on, plus the main 3.0 additions.
 */
@SuppressWarnings("deprecation")
@RunWith(AndroidJUnit4.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class JavaApiCompatibilityTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    @Test
    public void twoPointXJavaUsageStillCompilesAndWorks() {
        FlowTextView flowTextView = new FlowTextView(context);
        Spanned html = Html.fromHtml("<html>Your <b>html</b> goes here....");
        flowTextView.setText(html);
        flowTextView.setOnLinkClickListener(new OnLinkClickListener() {
            @Override
            public void onLinkClick(String url) {
            }
        });
        // setTextPaint copies every paint property, so it goes first.
        flowTextView.setTextPaint(new TextPaint());
        flowTextView.setLinkPaint(new TextPaint());
        flowTextView.setTextSize(flowTextView.getTextsize() + 1);
        flowTextView.setTextColor(Color.DKGRAY);
        flowTextView.setColor(Color.BLACK);
        flowTextView.setTypeface(Typeface.SERIF);

        CharSequence text = flowTextView.getText();
        float size = flowTextView.getTextsize();
        int color = flowTextView.getColor();
        int textColor = flowTextView.getTextColor();
        Typeface typeface = flowTextView.getTypeFace();
        TextPaint paint = flowTextView.getTextPaint();
        TextPaint linkPaint = flowTextView.getLinkPaint();
        int lineHeight = flowTextView.getLineHeight();
        OnLinkClickListener listener = flowTextView.getOnLinkClickListener();

        assertThat(text.toString()).isEqualTo("Your html goes here....");
        assertThat(listener).isNotNull();
        assertThat(lineHeight).isGreaterThan(0);
        assertThat(color).isEqualTo(textColor);
        assertThat(size).isEqualTo(paint.getTextSize());
        assertThat(typeface).isNotNull();
        assertThat(linkPaint).isNotNull();
    }

    @Test
    public void threePointOhApiIsUsableFromJava() {
        FlowTextView view = new FlowTextView(context, null);
        view.setOnLinkClickListener(url -> { });
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        view.setLineSpacing(4f, 1.2f);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setLinkTextColor(Color.BLUE);
        view.setHighlightColor(0x330000FF);
        view.setLetterSpacing(0.05f);
        view.setIncludeFontPadding(false);
        view.setBreakStrategy(0);
        view.setHyphenationFrequency(0);
        view.setJustificationMode(0);

        assertThat(view.getLineSpacingExtra()).isEqualTo(4f);
        assertThat(view.getIncludeFontPadding()).isFalse();
    }
}
