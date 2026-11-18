package app.template.extension.extension;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/** Gesture handling is attached to the window so translated views remain draggable. */
public final class MovableSpeedometer {
    private static final String PREFS = "morphe_speedometer_position";
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
        float startX, startY, lastX, lastY, lastSpan;
        final Runnable activate = () -> { if (armed) editing = true; };

        Controls(Activity activity, Window.Callback delegate) {
            this.activity = activity; this.delegate = delegate;
            prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }

        void findTarget() {
            int id = activity.getResources().getIdentifier("Speedometer", "id", activity.getPackageName());
            View found = id == 0 ? null : activity.findViewById(id);
            if (found == null || found == target) return;
            target = found;
            android.util.Log.i("QM-Speedometer", "Found speedometer view");
            for (ViewParent parent = target.getParent(); parent instanceof ViewGroup; parent = parent.getParent()) {
                ((ViewGroup) parent).setClipChildren(false);
                ((ViewGroup) parent).setClipToPadding(false);
            }
            restore();
        }

        void restore() {
            target.post(() -> {
                float density = target.getResources().getDisplayMetrics().density;
                target.setTranslationX(prefs.getFloat("x", 0f) * density);
                target.setTranslationY(prefs.getFloat("y", 0f) * density);
                float scale = prefs.getFloat("scale", 1f);
                target.setScaleX(scale); target.setScaleY(scale);
                anchored = prefs.contains("anchor_x") && prefs.contains("anchor_y");
                if (anchored) {
                    anchorX = prefs.getFloat("anchor_x", 0f);
                    anchorY = prefs.getFloat("anchor_y", 0f);
                } else if (prefs.contains("x") || prefs.contains("y")) {
                    // Migrate positions saved by earlier versions after layout completes.
                    rememberPosition();
                }
            });
        }

        void rememberPosition() {
            View decor = activity.getWindow().getDecorView();
            if (target == null || decor.getWidth() <= 0 || decor.getHeight() <= 0) return;
            int[] pos = new int[2];
            target.getLocationOnScreen(pos);
            anchorX = (float) pos[0] / decor.getWidth();
            anchorY = (float) pos[1] / decor.getHeight();
            anchored = true;
        }

        boolean stabilizePosition() {
            if (!anchored || editing || target == null || !target.isAttachedToWindow()) return true;
            View decor = activity.getWindow().getDecorView();
            if (decor.getWidth() <= 0 || decor.getHeight() <= 0) return true;
            int[] pos = new int[2];
            target.getLocationOnScreen(pos);
            float dx = anchorX * decor.getWidth() - pos[0];
            float dy = anchorY * decor.getHeight() - pos[1];
            if (Math.abs(dx) >= 1f) target.setTranslationX(target.getTranslationX() + dx);
            if (Math.abs(dy) >= 1f) target.setTranslationY(target.getTranslationY() + dy);
            return true;
        }

        boolean inside(MotionEvent event) {
            findTarget();
            if (target == null || !target.isShown()) return false;
            int[] pos = new int[2]; target.getLocationOnScreen(pos);
            float x = event.getRawX(), y = event.getRawY();
            return x >= pos[0] && y >= pos[1] && x <= pos[0] + target.getWidth() * target.getScaleX() && y <= pos[1] + target.getHeight() * target.getScaleY();
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
                    if (event.getPointerCount() >= 2) {
                        float span = span(event);
                        if (lastSpan > 0 && span > 0) {
                            float scale = Math.max(.5f, Math.min(2.5f, target.getScaleX() * span / lastSpan));
                            target.setScaleX(scale); target.setScaleY(scale); moved = true;
                        }
                        lastSpan = span;
                    } else {
                        target.setTranslationX(target.getTranslationX() + event.getRawX() - lastX);
                        target.setTranslationY(target.getTranslationY() + event.getRawY() - lastY);
                        moved = true;
                    }
                    lastX = event.getRawX(); lastY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    lastSpan = 0; lastX = event.getRawX(); lastY = event.getRawY(); return capturing;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(activate); armed = false;
                    boolean consumed = capturing;
                    if (editing && moved) {
                        float density = target.getResources().getDisplayMetrics().density;
                        rememberPosition();
                        prefs.edit().putFloat("x", target.getTranslationX() / density).putFloat("y", target.getTranslationY() / density).putFloat("scale", target.getScaleX()).putFloat("anchor_x", anchorX).putFloat("anchor_y", anchorY).apply();
                    }
                    editing = false; capturing = false; return consumed;
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
}
