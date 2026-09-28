package com.perez.util;

import android.app.Activity;
import android.app.Application;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;

public class EdgeToEdgeCompatHelper {

    /**
     * Call this in the Application to provide a global, one-time fix for Android 15/16 edge-to-edge display issues.
     */
    public static void install(Application app) {
        // Only takes effect on Android 15 (API 35) and Android 16+ where edge-to-edge is enforced
        // Android 14 and earlier versions default to decorFitsSystemWindows=true, strictly maintaining native behavior to avoid breaking existing UI
        if (Build.VERSION.SDK_INT >= 35) {
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

                @Override
                public void onActivityStarted(Activity activity) {
                    applySystemBarsPadding(activity);
                }

                @Override
                public void onActivityResumed(Activity activity) {}

                @Override
                public void onActivityPaused(Activity activity) {}

                @Override
                public void onActivityStopped(Activity activity) {}

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

                @Override
                public void onActivityDestroyed(Activity activity) {}
            });
        }
    }

    private static void applySystemBarsPadding(Activity activity) {
        if (activity == null || activity.getWindow() == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            View decorView = activity.getWindow().getDecorView();

            decorView.setOnApplyWindowInsetsListener((v, insets) -> {
                // Capture pixel insets for the status bar, navigation bar, and display cutout
                int insetsType = WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout();
                Insets systemBars = insets.getInsets(insetsType);

                // Constrain the DecorView to the system bar safe area
                // For pages with an ActionBar, the ActionBar sits directly below the status bar
                // For pages without an ActionBar, content also begins below the status bar and the bottom edge is positioned above the navigation bar.
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);

                // Set consumed systemBars insets to NONE in order to prevent internal views from applying padding a second time,
                // while allowing other insets—such as those for the IME (soft keyboard)—to continue being dispatched to the EditText container.
                return new WindowInsets.Builder(insets)
                        .setInsets(insetsType, Insets.NONE)
                        .build();
            });

            decorView.requestApplyInsets();
        }
    }
}