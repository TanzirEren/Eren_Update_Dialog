package com.eren.dialog;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        TextView info = new TextView(this);
        info.setText("Eren dialog host\n\nHOOK: Eren.show(this);");
        info.setGravity(Gravity.CENTER);
        info.setTextSize(18);
        setContentView(info);

        Eren.show(this);
    }

    @Override
    protected void onDestroy() {
        Eren.stop();
        super.onDestroy();
    }
}
