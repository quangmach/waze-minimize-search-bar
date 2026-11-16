package app.template.extension.extension;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Window;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class SearchSheet {
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static WeakReference<Object> collector = new WeakReference<>(null);
    private static final long TIMEOUT_MS = 10000;
    private static boolean down;
    private static final Runnable AUTO_HIDE = () -> {
        Object item = collector.get();
        if (item == null || down) return;
        try {
            Object state = sheet(item);
            Object settled = state.getClass().getMethod("h").invoke(state);
            if (!(settled instanceof Enum) || "Hidden".equals(((Enum<?>) settled).name())) return;
            Class<?> click = Class.forName("com.waze.map.b.t");
            Object event = click.getField("a").get(null);
            Class<?> continuation = Class.forName("h.c.e");
            Method emit = item.getClass().getDeclaredMethod("emit", Object.class, continuation);
            emit.setAccessible(true);
            emit.invoke(item, event, null);
        } catch (Throwable e) { Log.w("WazeSearchPatch", "Auto-hide failed", e); }
    };

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
    private static Object sheet(Object item) throws Exception {
        Object screen = field(item, "a");
        Object holder = screen.getClass().getMethod("a").invoke(screen);
        return holder.getClass().getMethod("c").invoke(holder);
    }
    private static void schedule() {
        HANDLER.removeCallbacks(AUTO_HIDE);
        if (!down && collector.get() != null) HANDLER.postDelayed(AUTO_HIDE, TIMEOUT_MS);
    }
    public static void register(Object item) {
        try {
            if (((Integer) field(item, "c")) != 1) return;
            collector = new WeakReference<>(item);
            schedule();
        } catch (Throwable e) { Log.w("WazeSearchPatch", "Registration failed", e); }
    }
    public static Object toggle(Object holder, Object continuation) throws Throwable {
        try {
            Object state = holder.getClass().getMethod("c").invoke(holder);
            Object current = state.getClass().getMethod("i").invoke(state);
            boolean hidden = current instanceof Enum && "Hidden".equals(((Enum<?>) current).name());
            Class<?> cont = Class.forName("h.c.e");
            Object result = state.getClass().getMethod(hidden ? "o" : "m", cont).invoke(state, continuation);
            schedule();
            return result;
        } catch (InvocationTargetException e) { throw e.getCause(); }
    }
    public static void install(Activity activity) {
        try {
            Window window = activity.getWindow();
            Window.Callback original = window.getCallback();
            if (original == null || Proxy.isProxyClass(original.getClass())) return;
            Window.Callback wrapped = (Window.Callback) Proxy.newProxyInstance(
                Window.Callback.class.getClassLoader(), new Class<?>[]{Window.Callback.class}, (proxy, method, args) -> {
                    if ("dispatchTouchEvent".equals(method.getName()) && args != null && args[0] instanceof MotionEvent) {
                        int action = ((MotionEvent) args[0]).getActionMasked();
                        if (action == MotionEvent.ACTION_DOWN) { down = true; HANDLER.removeCallbacks(AUTO_HIDE); }
                        else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) { down = false; schedule(); }
                    }
                    try { return method.invoke(original, args); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                });
            window.setCallback(wrapped);
        } catch (Throwable e) { Log.w("WazeSearchPatch", "Touch monitoring unavailable", e); }
    }
}
