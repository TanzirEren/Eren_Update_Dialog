package com.eren.dialog;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * ============================================================================
 *  EREN  -  host activity
 * ============================================================================
 *
 *  This activity does nothing except host the update dialog. It is the only
 *  entry point you need in a modded APK:
 *
 *      Eren.show(this);                                    // CONNECT_KEY
 *      Eren.show(this, "MM-XXX-XXX-XXX-ST");               // explicit key
 *      Eren.show(this, key, new Eren.Callback() { ... });  // with callbacks
 *
 *  The dialog stays on screen until the user updates or exits, so the user is
 *  forced to see it on every launch of the host app.
 * ============================================================================
 */
public class MainActivity extends Activity {

    private boolean userExited;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        Window w = getWindow();
        w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        /* a calm backdrop; the dialog paints its own scrim on top of it */
        FrameLayout host = new FrameLayout(this);
        host.setBackgroundColor(Color.parseColor("#FFD0C8"));

        TextView hint = new TextView(this);
        hint.setText("EREN");
        hint.setTextColor(Color.parseColor("#33FFFFFF"));
        hint.setTextSize(28f);
        hint.setGravity(Gravity.CENTER);
        host.addView(hint, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        host.setVisibility(View.VISIBLE);

        setContentView(host);

        /* ------------------------------------------------ the dialog */
        Eren.show(this, Eren.CONNECT_KEY, new Eren.Callback() {

            @Override
            public void onConfigLoaded(Eren.Cfg cfg) {
                Eren.log("config ready: " + cfg.brand + " / " + cfg.appName);
            }

            @Override
            public void onExit() {
                userExited = true;
            }

            @Override
            public void onUpdate(String url) {
                /* the dialog already opened the URL; add extra logic here if needed */
            }

            @Override
            public void onDismiss() {
                /* Once the dialog is gone the host app closes, so the user cannot
                 * keep using a build that still needs the update. */
                if (!userExited) {
                    finish();
                }
            }

            @Override
            public void onError(String message) {
                Eren.log("dialog error: " + message);
            }
        });
    }

    @Override
    public void onBackPressed() {
        /* back behaves like EXIT while the dialog is up */
        if (Eren.isShowing()) {
            Eren.dismiss();
            return;
        }
        super.onBackPressed();
    }
}
