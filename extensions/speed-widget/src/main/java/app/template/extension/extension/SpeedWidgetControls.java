package app.template.extension.extension;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

public final class SpeedWidgetControls extends Activity {
    private static final int LOCATION = 1;
    private boolean configuring;
    private boolean requestedSetup;
    private int widgetId;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        configuring = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE.equals(getIntent().getAction());
        if (!SpeedWidget.isBound(this, widgetId)) {
            finish();
            return;
        }
        if (configuring) {
            setResult(RESULT_CANCELED);
            return;
        }
        boolean running = SpeedWidgetService.isTracking();
        new AlertDialog.Builder(this)
                .setTitle("Waze Speedometer")
                .setMessage(running
                        ? "GPS speed is being tracked for the launcher widget."
                        : "Use your location for live GPS speed and available Waze speed limits.")
                .setPositiveButton(running ? "Stop" : "Start", (dialog, button) -> {
                    if (running) {
                        startService(new Intent(this, SpeedWidgetService.class).setAction(SpeedWidgetService.STOP));
                        finish();
                    } else if (hasLocation()) {
                        startTracking();
                    } else {
                        requestLocation();
                    }
                })
                .setNeutralButton("Auto tracking", (dialog, button) -> {
                    startActivity(new Intent(this, SpeedWidgetAutoSettings.class));
                    finish();
                })
                .setNegativeButton("Cancel", (dialog, button) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (!configuring || requestedSetup || isFinishing()) return;
        requestedSetup = true;
        if (SpeedWidgetAuto.armed(this)) {
            SpeedWidgetAuto.observe(this);
            SpeedWidgetBluetooth.checkConnectedAudio(getApplicationContext());
            finishSetup();
        } else if (hasLocation()) startTracking();
        else requestLocation();
    }

    private void requestLocation() {
        requestPermissions(new String[] {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }, LOCATION);
    }

    private boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void startTracking() {
        Intent service = new Intent(this, SpeedWidgetService.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(service);
            } else {
                startService(service);
            }
            finishSetup();
        } catch (SecurityException | IllegalStateException e) {
            new AlertDialog.Builder(this)
                    .setMessage("Android could not start GPS tracking.")
                    .setPositiveButton("OK", (dialog, button) -> finishSetup())
                    .setOnCancelListener(dialog -> finishSetup())
                    .show();
        }
    }

    private void finishSetup() {
        if (configuring) {
            SpeedWidgetService.refresh(this);
            setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        }
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == LOCATION) {
            if (hasLocation()) {
                startTracking();
            } else if (configuring) {
                finishSetup();
            } else {
                new AlertDialog.Builder(this)
                        .setMessage("Allow precise location to see live GPS speed.")
                        .setPositiveButton("OK", (dialog, button) -> finish())
                        .setOnCancelListener(dialog -> finish())
                        .show();
            }
        }
    }
}
