package app.template.extension.extension;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/** Gesture handling is attached to the window so translated views remain draggable. */
public final class MovableSpeedometer {
    private static final String PREFS = "morphe_speedometer_position";
    private static final long REFRESH_MS = 100;
    private MovableSpeedometer() {}

    public static void install(Activity activity) {
        activity.getWindow().getDecorView().post(() -> attach(activity));
    }

    private static void attach(Activity activity) {
        try {
            Window window = activity.getWindow();
            Window.Callback callback = window.getCallback();
            if (Proxy.isProxyClass(callback.getClass()) && Proxy.getInvocationHandler(callback) instanceof Controls) {
                ((Controls) Proxy.getInvocationHandler(callback)).findTarget();
                return;
            }
            Controls controls = new Controls(activity, callback);
            window.setCallback((Window.Callback) Proxy.newProxyInstance(Window.Callback.class.getClassLoader(), new Class<?>[]{Window.Callback.class}, controls));
            android.util.Log.i("QM-Speedometer", "Installed on map activity");
            View decor = window.getDecorView();
            decor.getViewTreeObserver().addOnGlobalLayoutListener(controls::findTarget);
            decor.getViewTreeObserver().addOnPreDrawListener(controls::stabilizePosition);
            controls.findTarget();
        } catch (RuntimeException error) {
            android.util.Log.w("QM-Speedometer", "Could not install speedometer controls", error);
        }
    }

    private static final class Controls implements java.lang.reflect.InvocationHandler {
        final Activity activity;
        final Window.Callback delegate;
        final Handler handler = new Handler(Looper.getMainLooper());
        final SharedPreferences prefs;
        View target;
        boolean armed, capturing, editing, moved, anchored;
        float anchorX, anchorY;
        float x, y, scale = 1f;
        String placementKey;
        DialOverlay overlay;
        float originalAlpha = 1f;
        final int[] targetLocation = new int[2];
        final int[] decorLocation = new int[2];
        float startX, startY, lastX, lastY, lastSpan;
        final Runnable activate = () -> { if (armed && target != null) editing = true; };
        final Runnable refresh = new Runnable() {
            @Override public void run() {
                if (overlay == null) return;
                if (!target.isShown() || target.getWindowVisibility() != View.VISIBLE) {
                    removeOverlay();
                    return;
                }
                overlay.invalidateSelf();
                handler.postDelayed(this, REFRESH_MS);
            }
        };

        Controls(Activity activity, Window.Callback delegate) {
            this.activity = activity; this.delegate = delegate;
            prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }

        void findTarget() {
            int id = activity.getResources().getIdentifier("Speedometer", "id", activity.getPackageName());
            View found = id == 0 ? null : activity.findViewById(id);
            if (found == target) return;
            handler.removeCallbacks(activate);
            removeOverlay();
            armed = editing = moved = false;
            target = found;
            placementKey = null;
            if (target == null) return;
            android.util.Log.i("QM-Speedometer", "Found speedometer view");
        }

        private void removeOverlay() {
            if (overlay == null) return;
            handler.removeCallbacks(refresh);
            activity.getWindow().getDecorView().getOverlay().remove(overlay);
            if (Build.VERSION.SDK_INT >= 29) target.setTransitionAlpha(originalAlpha);
            else target.setAlpha(originalAlpha);
            overlay = null;
        }

        private void updateOverlay() {
            View decor = activity.getWindow().getDecorView();
            if ((!anchored && !moved) || !target.isShown() || decor.getWindowVisibility() != View.VISIBLE) {
                removeOverlay();
                return;
            }
            // Draw the moved dial above Waze while its source keeps its original layout and bounds.
            if (overlay == null) {
                originalAlpha = Build.VERSION.SDK_INT >= 29 ? target.getTransitionAlpha() : target.getAlpha();
                overlay = new DialOverlay(target, originalAlpha);
                decor.getOverlay().add(overlay);
                if (Build.VERSION.SDK_INT >= 29) target.setTransitionAlpha(0f);
                else target.setAlpha(0f);
                handler.postDelayed(refresh, REFRESH_MS);
            }
            float width = target.getWidth() * scale;
            float height = target.getHeight() * scale;
            x = clamp(x, decor.getWidth() - width);
            y = clamp(y, decor.getHeight() - height);
            overlay.setBounds(Math.round(x), Math.round(y), Math.round(x + width), Math.round(y + height));
        }

        private void restore(View decor, String key) {
            // Display IDs can change when an external or virtual display reconnects.
            placementKey = key;
            String saved = key;
            if (!prefs.contains(saved + "anchor_x")) saved = "primary_" + key.substring(key.indexOf('_') + 1);
            if (!prefs.contains(saved + "anchor_x")) saved = "primary_tall_";
            if (!prefs.contains(saved + "anchor_x")) saved = "";
            scale = prefs.getFloat(saved + "scale", 1f);
            anchored = prefs.contains(saved + "anchor_x") && prefs.contains(saved + "anchor_y");
            boolean legacyPosition = prefs.contains("x") || prefs.contains("y");
            if (anchored) {
                anchorX = prefs.getFloat(saved + "anchor_x", 0f);
                anchorY = prefs.getFloat(saved + "anchor_y", 0f);
            } else if (legacyPosition || scale != 1f) {
                nativePosition(decor);
                float density = target.getResources().getDisplayMetrics().density;
                x += prefs.getFloat("x", 0f) * density;
                y += prefs.getFloat("y", 0f) * density;
                rememberPosition(decor);
            }
        }

        private void nativePosition(View decor) {
            target.getLocationOnScreen(targetLocation);
            decor.getLocationOnScreen(decorLocation);
            x = targetLocation[0] - decorLocation[0];
            y = targetLocation[1] - decorLocation[1];
        }

        private void rememberPosition(View decor) {
            anchorX = clamp(x, decor.getWidth() - target.getWidth() * scale) / decor.getWidth();
            anchorY = clamp(y, decor.getHeight() - target.getHeight() * scale) / decor.getHeight();
            anchored = true;
        }

        private static float clamp(float position, float max) {
            return Math.max(0f, Math.min(position, max));
        }

        boolean stabilizePosition() {
            if (target == null || !target.isAttachedToWindow() || !target.isLaidOut()) {
                removeOverlay();
                return true;
            }
            View decor = activity.getWindow().getDecorView();
            if (decor.getWidth() <= 0 || decor.getHeight() <= 0) return true;
            Display display = decor.getDisplay();
            if (display == null) return true;
            String key = (display.getDisplayId() == Display.DEFAULT_DISPLAY ? "primary_" : "secondary_")
                    + (decor.getWidth() > decor.getHeight() ? "wide_" : "tall_");
            if (!key.equals(placementKey)) {
                handler.removeCallbacks(activate);
                armed = editing = moved = false;
                restore(decor, key);
            }
            if (anchored && !editing) {
                x = anchorX * decor.getWidth();
                y = anchorY * decor.getHeight();
            }
            updateOverlay();
            return true;
        }

        boolean inside(MotionEvent event) {
            findTarget();
            if (target == null || !target.isShown()) return false;
            float touchX = event.getRawX(), touchY = event.getRawY();
            if (overlay != null) {
                activity.getWindow().getDecorView().getLocationOnScreen(decorLocation);
                return overlay.getBounds().contains((int) (touchX - decorLocation[0]), (int) (touchY - decorLocation[1]));
            }
            target.getLocationOnScreen(targetLocation);
            return touchX >= targetLocation[0] && touchY >= targetLocation[1]
                    && touchX <= targetLocation[0] + target.getWidth() * target.getScaleX()
                    && touchY <= targetLocation[1] + target.getHeight() * target.getScaleY();
        }

        boolean touch(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    armed = inside(event); capturing = armed; editing = false; moved = false; lastSpan = 0;
                    startX = lastX = event.getRawX(); startY = lastY = event.getRawY();
                    if (armed) handler.postDelayed(activate, ViewConfiguration.getLongPressTimeout());
                    return capturing;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (editing && event.getPointerCount() >= 2) lastSpan = span(event);
                    return capturing;
                case MotionEvent.ACTION_MOVE:
                    if (!editing) {
                        float dx = event.getRawX() - startX, dy = event.getRawY() - startY;
                        int slop = ViewConfiguration.get(activity).getScaledTouchSlop();
                        if (dx * dx + dy * dy > slop * slop) { armed = false; handler.removeCallbacks(activate); }
                        return capturing;
                    }
                    View decor = activity.getWindow().getDecorView();
                    if (!moved && !anchored) nativePosition(decor);
                    if (event.getPointerCount() >= 2) {
                        float span = span(event);
                        if (lastSpan > 0 && span > 0) {
                            scale = Math.max(.5f, Math.min(2.5f, scale * span / lastSpan));
                            moved = true;
                        }
                        lastSpan = span;
                    } else {
                        x += event.getRawX() - lastX;
                        y += event.getRawY() - lastY;
                        moved = true;
                    }
                    lastX = event.getRawX(); lastY = event.getRawY();
                    updateOverlay();
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    lastSpan = 0; lastX = event.getRawX(); lastY = event.getRawY(); return capturing;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(activate); armed = false;
                    boolean consumed = capturing;
                    if (event.getActionMasked() == MotionEvent.ACTION_UP && editing && moved && placementKey != null) {
                        rememberPosition(activity.getWindow().getDecorView());
                        prefs.edit().putFloat(placementKey + "scale", scale)
                                .putFloat(placementKey + "anchor_x", anchorX)
                                .putFloat(placementKey + "anchor_y", anchorY).apply();
                    }
                    editing = moved = capturing = false;
                    return consumed;
                default: return capturing;
            }
        }

        float span(MotionEvent event) {
            float dx = event.getX(0) - event.getX(1), dy = event.getY(0) - event.getY(1);
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        @Override public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
            if ("dispatchTouchEvent".equals(method.getName()) && args != null && touch((MotionEvent) args[0])) return true;
            try { return method.invoke(delegate, args); } catch (InvocationTargetException e) { throw e.getCause(); }
        }
    }

    private static final class DialOverlay extends Drawable {
        private final View source;
        private final float initialAlpha;

        DialOverlay(View source, float initialAlpha) {
            this.source = source;
            this.initialAlpha = initialAlpha;
        }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            if (source.getWidth() == 0 || source.getHeight() == 0 || bounds.isEmpty()) return;
            float alpha = Build.VERSION.SDK_INT >= 29 ? source.getAlpha() * initialAlpha : initialAlpha;
            if (alpha <= 0f) return;
            int save = canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.scale((float) bounds.width() / source.getWidth(), (float) bounds.height() / source.getHeight());
            if (alpha < 1f) canvas.saveLayerAlpha(0, 0, source.getWidth(), source.getHeight(), Math.round(alpha * 255));
            source.draw(canvas);
            canvas.restoreToCount(save);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
