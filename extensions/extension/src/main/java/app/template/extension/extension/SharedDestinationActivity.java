package app.template.extension.extension;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class SharedDestinationActivity extends Activity {
    private static final String TAG = "QM-WazeDestination";
    private static final String WAZE_ENTRY = "com.waze.FreeMapAppActivity";
    private static final Set<Activity> visibleActivities =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean tracking;

    public static void trackDisplay(Activity mapActivity) {
        visibleActivities.add(mapActivity);
        if (tracking) return;
        tracking = true;
        mapActivity.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity activity) { visibleActivities.add(activity); }
            @Override public void onActivityStopped(Activity activity) { visibleActivities.remove(activity); }
            @Override public void onActivityDestroyed(Activity activity) { visibleActivities.remove(activity); }
            @Override public void onActivityCreated(Activity activity, Bundle state) {}
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            forwardDestination(getIntent());
        } finally {
            finish();
        }
    }

    private void forwardDestination(Intent source) {
        String action = source.getAction();
        if (!Intent.ACTION_VIEW.equals(action) && !Intent.ACTION_SEND.equals(action)
                && !Intent.ACTION_SEARCH.equals(action) && !"android.intent.action.NAVIGATE".equals(action)) return;

        Intent destination = new Intent(source);
        destination.setSelector(null);
        destination.setClassName(getPackageName(), WAZE_ENTRY);
        destination.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | (source.getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)));

        Uri referrer = getReferrer();
        if (referrer != null && !destination.hasExtra(Intent.EXTRA_REFERRER)) {
            destination.putExtra(Intent.EXTRA_REFERRER, referrer);
        }

        if (Build.VERSION.SDK_INT >= 26) {
            int displayId = visibleAnyAutoDisplay();
            if (displayId != Display.INVALID_DISPLAY) {
                try {
                    Bundle options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle();
                    startActivity(destination, options);
                    Log.i(TAG, "Opened shared destination on AnyAuto display " + displayId);
                    return;
                } catch (SecurityException | IllegalArgumentException error) {
                    Log.w(TAG, "AnyAuto display is unavailable", error);
                }
            }
        }
        startActivity(destination);
    }

    private static int visibleAnyAutoDisplay() {
        for (Activity activity : visibleActivities) {
            if (activity instanceof SharedDestinationActivity || activity.isFinishing()) continue;
            Display display = activity.getWindowManager().getDefaultDisplay();
            if (display != null && display.isValid() && display.getState() == Display.STATE_ON
                    && "AnyAuto".equals(display.getName())) {
                return display.getDisplayId();
            }
        }
        return Display.INVALID_DISPLAY;
    }
}
