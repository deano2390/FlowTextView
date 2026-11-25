package uk.co.deanwild.flowtextview.sample;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.text.Html;
import android.text.Spanned;
import android.view.View;
import android.widget.Button;

import uk.co.deanwild.flowtextview.FlowTextView;
import uk.co.deanwild.flowtextview.listeners.OnLinkClickListener;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final float defaultFontSize = 20.0f;

    private FlowTextView flowTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        flowTextView = (FlowTextView) findViewById(R.id.ftv);
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
        // Resource IDs are no longer constants (non-final R classes), so a switch cannot be used.
        int id = v.getId();
        if (id == R.id.btn_increase_font_size) {
            increaseFontSize();
        } else if (id == R.id.btn_decrease_font_size) {
            decreaseFontSize();
        } else if (id == R.id.btn_reset) {
            reset();
        }
    }

    private void increaseFontSize(){
        float currentFontSize = flowTextView.getTextSize();
        flowTextView.setTextSize(currentFontSize+1);
    }

    private void decreaseFontSize(){
        float currentFontSize = flowTextView.getTextSize();
        flowTextView.setTextSize(currentFontSize-1);
    }

    private void reset(){
        flowTextView.setTextSize(defaultFontSize);
    }
}
