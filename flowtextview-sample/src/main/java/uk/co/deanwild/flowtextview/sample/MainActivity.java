package uk.co.deanwild.flowtextview.sample;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.text.Html;
import android.text.Spanned;
import android.view.View;
import android.widget.Button;

import uk.co.deanwild.flowtextview.FlowTextView;
import uk.co.deanwild.flowtextview.listeners.OnLinkClickListener;


public class MainActivity extends ActionBarActivity implements View.OnClickListener {

    private static final float defaultFontSize = 20.0f;

    private FlowTextView flowTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        flowTextView = (FlowTextView) findViewById(R.id. ftv);
        String content = getString(R.string.lorem);
        Spanned html = Html.fromHtml(content);
        flowTextView.setText(html);


        // handle link behaviour
        flowTextView.setOnLinkClickListener(new OnLinkClickListener() {
            @Override
            public void onLinkClick(String url) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);
            }
        });

        Button btnIncreasefontSize = (Button) findViewById(R.id.btn_increase_font_size);
        btnIncreasefontSize.setOnClickListener(this);
        Button btnDecreasefontSize = (Button) findViewById(R.id.btn_decrease_font_size);
        btnDecreasefontSize.setOnClickListener(this);
        Button btnReset = (Button) findViewById(R.id.btn_reset);
        btnReset.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.btn_increase_font_size:
                increaseFontSize();
                break;
            case R.id.btn_decrease_font_size:
                decreaseFontSize();
                break;
            case R.id.btn_reset:
                reset();
                break;
            default:
                break;
        }
    }

    private void increaseFontSize(){
        float currentFontSize = flowTextView.getTextsize();
        flowTextView.setTextSize(currentFontSize+1);
    }

    private void decreaseFontSize(){
        float currentFontSize = flowTextView.getTextsize();
        flowTextView.setTextSize(currentFontSize-1);
    }

    private void reset(){
        flowTextView.setTextSize(defaultFontSize);
    }
}
