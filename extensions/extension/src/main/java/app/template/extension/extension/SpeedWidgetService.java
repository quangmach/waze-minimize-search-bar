package app.template.extension.extension;

import android.app.Notification;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class SpeedWidgetService extends Service implements LocationListener {
    private static final int NOTIFICATION_ID = 0x514D5701;
    private static final String CHANNEL = "qm_waze_speed_widget";
    private static final String STOP = "app.template.extension.extension.STOP_SPEED_WIDGET";
    private static final String UNAVAILABLE = "—";
    private static final long MAX_FIX_AGE_MS = 6000;
    private static final float MAX_LIMIT_DISTANCE_METERS = 25;
    private static volatile Reading wazeReading;
    private static SpeedWidgetService running;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            publish(false);
            handler.postDelayed(this, 1000);
        }
    };
    private LocationManager locationManager;
    private Location gps;
    private String rendered;

    private static final class Reading {
        final String unit;
        final int limit;
        final Location fix;
        final long timestamp;

        Reading(String unit, int limit, Location fix) {
            this.unit = unit;
            this.limit = limit;
            this.fix = fix == null ? null : new Location(fix);
            this.timestamp = SystemClock.elapsedRealtime();
        }
    }

    // Called from Waze's JNI-backed speedometer update, with the associated Waze location fix.
    public static void onWazeLimit(String units, int limit, Location location) {
        wazeReading = new Reading(normalizeUnit(units), limit, location);
    }

    static boolean isRunning() {
        return running != null;
    }

    static void refresh(Context context) {
        if (running == null) {
            SpeedWidget.render(context, UNAVAILABLE, defaultUnit(), UNAVAILABLE, "Tap to start");
        } else {
            running.publish(true);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (running == this) {
            return START_STICKY;
        }

        try {
            startForeground(NOTIFICATION_ID, notification());
            if (AppWidgetManager.getInstance(this).getAppWidgetIds(SpeedWidget.component(this)).length == 0) {
                stopSelf();
                return START_NOT_STICKY;
            }
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper());
        } catch (IllegalArgumentException | SecurityException | IllegalStateException e) {
            stopSelf();
            return START_NOT_STICKY;
        }

        running = this;
        tick.run();
        try {
            startWazeEngine(getApplication());
        } catch (RuntimeException | LinkageError e) {
            Log.w("QmWazeSpeedWidget", "Waze could not start for road limits", e);
        }
        return START_STICKY;
    }

    // Morphe inserts the call to Waze's coroutine initializer here.
    private static void startWazeEngine(Application application) {}

    private static Object newContinuation(Class<?> continuation, Object emptyContext) {
        return Proxy.newProxyInstance(continuation.getClassLoader(), new Class<?>[] { continuation },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getContext": return emptyContext;
                        case "resumeWith": return null;
                        case "toString": return "Waze speed widget initializer";
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    private Notification notification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Waze Speedometer", NotificationManager.IMPORTANCE_LOW));
            builder = new Notification.Builder(this, CHANNEL);
        } else {
            builder = new Notification.Builder(this);
        }
        Intent stop = new Intent(this, SpeedWidgetService.class).setAction(STOP);
        PendingIntent stopAction = PendingIntent.getService(this, 0, stop, PendingIntent.FLAG_IMMUTABLE);
        return builder.setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle("Waze Speedometer")
                .setContentText("Updating the launcher widget")
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Stop", stopAction).build())
                .build();
    }

    @Override
    public void onLocationChanged(Location location) {
        gps = location;
        publish(false);
    }

    @Override
    public void onProviderDisabled(String provider) {
        gps = null;
        publish(false);
    }

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    private void publish(boolean force) {
        long now = SystemClock.elapsedRealtime();
        boolean gpsReady = isFresh(gps, now);
        Reading reading = wazeReading;
        boolean currentLimit = gpsReady && reading != null && now - reading.timestamp <= MAX_FIX_AGE_MS
                && reading.limit > 0 && reading.unit != null && isFresh(reading.fix, now)
                && reading.fix.distanceTo(gps) <= MAX_LIMIT_DISTANCE_METERS;
        String unit = reading == null || reading.unit == null ? defaultUnit() : reading.unit;
        String speed = gpsReady && gps.hasSpeed() && gps.getSpeed() >= 0 && Float.isFinite(gps.getSpeed())
                ? Integer.toString(Math.round(gps.getSpeed() * ("mph".equals(unit) ? 2.2369363f : 3.6f)))
                : UNAVAILABLE;
        String limit = currentLimit ? Integer.toString(reading.limit) : UNAVAILABLE;
        String status = UNAVAILABLE.equals(speed) ? "Waiting for GPS"
                : currentLimit ? "Tracking" : "Waze limit unavailable";
        String display = speed + "|" + unit + "|" + limit + "|" + status;
        if (force || !display.equals(rendered)) {
            rendered = display;
            SpeedWidget.render(this, speed, unit, limit, status);
        }
    }

    private static boolean isFresh(Location fix, long now) {
        if (fix == null || !fix.hasAccuracy() || fix.getAccuracy() > 35) {
            return false;
        }
        long age = now - TimeUnit.NANOSECONDS.toMillis(fix.getElapsedRealtimeNanos());
        return age >= 0 && age <= MAX_FIX_AGE_MS;
    }

    private static String normalizeUnit(String unit) {
        if (unit == null) {
            return null;
        }
        switch (unit.trim().toLowerCase(Locale.ROOT)) {
            case "mph": return "mph";
            case "km/h":
            case "kmh":
            case "kph": return "km/h";
            default: return null;
        }
    }

    private static String defaultUnit() {
        String country = Locale.getDefault().getCountry();
        return "US".equals(country) || "GB".equals(country) ? "mph" : "km/h";
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
        if (running == this) {
            running = null;
        }
        refresh(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
