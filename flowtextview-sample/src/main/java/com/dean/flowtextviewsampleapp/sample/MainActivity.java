package com.dean.flowtextviewsampleapp.sample;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;

import com.deanwild.flowtext.FlowTextView;


public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FlowTextView flowTextView = (FlowTextView) findViewById(R.id. ftv);
        flowTextView.setText(getString(R.string.lorem));

    }
}
