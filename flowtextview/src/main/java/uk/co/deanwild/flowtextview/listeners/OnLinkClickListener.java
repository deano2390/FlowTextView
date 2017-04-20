package uk.co.deanwild.flowtextview.listeners;

import android.content.Intent;
import android.net.Uri;
import android.view.View;

/**
 * Created by Dean on 24/06/2014.
 */
public interface OnLinkClickListener {

    OnLinkClickListener DEFAULT = new OnLinkClickListener() {
        @Override
        public void onLinkClick(View view, String url) {
            view.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        }
    };

    void onLinkClick(View view, String url);
}
