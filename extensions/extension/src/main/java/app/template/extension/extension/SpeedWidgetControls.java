package app.template.extension.extension;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

public final class SpeedWidgetControls extends Activity {
    private static final int LOCATION = 1;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        boolean running = SpeedWidgetService.isRunning();
        new AlertDialog.Builder(this)
                .setTitle("Waze Speedometer")
                .setMessage(running
                        ? "GPS speed is being tracked for the launcher widget."
                        : "Use your location for live GPS speed and available Waze speed limits.")
                .setPositiveButton(running ? "Stop" : "Start", (dialog, button) -> {
                    if (running) {
                        stopService(new Intent(this, SpeedWidgetService.class));
                        finish();
                    } else if (hasLocation()) {
                        startTracking();
                    } else {
                        requestPermissions(new String[] {
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        }, LOCATION);
                    }
                })
                .setNegativeButton("Cancel", (dialog, button) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void startTracking() {
        Intent service = new Intent(this, SpeedWidgetService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(service);
            } else {
                startService(service);
            }
            finish();
        } catch (SecurityException | IllegalStateException e) {
            new AlertDialog.Builder(this)
                    .setMessage("Android could not start GPS tracking.")
                    .setPositiveButton("OK", (dialog, button) -> finish())
                    .setOnCancelListener(dialog -> finish())
                    .show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == LOCATION) {
            if (hasLocation()) {
                startTracking();
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
