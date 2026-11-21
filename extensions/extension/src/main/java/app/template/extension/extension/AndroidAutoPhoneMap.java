package app.template.extension.extension;

import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class AndroidAutoPhoneMap {
    private static final String TAG = "QM-CarPhoneUi";
    private static final String MAIN_SESSION = "com.waze.car_lib.v";
    private static final String PROJECTION = "com.waze.ak";

    private static Object visibleSession;
    private static volatile boolean carScreenVisible;
    private static Bindings bindings;

    private AndroidAutoPhoneMap() {}

    public static synchronized void onSessionEvent(Object session, Enum<?> event) {
        if (!MAIN_SESSION.equals(session.getClass().getName())) return;

        switch (event.name()) {
            case "ON_START":
            case "ON_RESUME":
                visibleSession = session;
                break;
            case "ON_STOP":
            case "ON_DESTROY":
                if (visibleSession == session) visibleSession = null;
                break;
            default:
                return;
        }

        boolean visible = visibleSession != null;
        if (carScreenVisible == visible) return;
        carScreenVisible = visible;
        Log.i(TAG, "Waze visible on car screen: " + visible);

        if (bindings == null) return;
        try {
            bindings.setVisible.invoke(bindings.visibilityFlow, visible);
        } catch (ReflectiveOperationException | LinkageError error) {
            Log.w(TAG, "Could not update phone connection state", error);
        }
    }

    public static Object initialConnection(Object connection) {
        if (carScreenVisible || !isProjection(connection)) return connection;
        try {
            return getBindings().disconnected;
        } catch (ReflectiveOperationException | LinkageError error) {
            Log.w(TAG, "Could not read Waze’s phone connection state", error);
            return connection;
        }
    }

    public static Object phoneConnectionFlow(Object connectionFlow) {
        try {
            Bindings current = getBindings();
            return current.combine.invoke(null, connectionFlow, current.visibilityFlow, current.transform);
        } catch (ReflectiveOperationException | LinkageError error) {
            Log.w(TAG, "Could not observe Waze’s phone connection state", error);
            return connectionFlow;
        }
    }

    private static boolean isProjection(Object connection) {
        return connection != null && PROJECTION.equals(connection.getClass().getName());
    }

    private static synchronized Bindings getBindings() throws ReflectiveOperationException {
        if (bindings == null) bindings = new Bindings(carScreenVisible);
        return bindings;
    }

    private static final class Bindings {
        final Object disconnected;
        final Object visibilityFlow;
        final Object transform;
        final Method combine;
        final Method setVisible;

        Bindings(boolean initiallyVisible) throws ReflectiveOperationException {
            disconnected = Class.forName("com.waze.aj").getField("a").get(null);
            Class<?> flow = Class.forName("kotlinx.coroutines.b.j");
            Class<?> function = Class.forName("g.h.a.q");
            combine = Class.forName("kotlinx.coroutines.b.af").getMethod("s", flow, flow, function);
            setVisible = Class.forName("kotlinx.coroutines.b.bj").getMethod("e", Object.class);
            visibilityFlow = Class.forName("kotlinx.coroutines.b.cf").getMethod("b", Object.class)
                    .invoke(null, initiallyVisible);
            transform = Proxy.newProxyInstance(function.getClassLoader(), new Class<?>[]{function},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "invoke":
                                return Boolean.FALSE.equals(args[1]) && isProjection(args[0])
                                        ? disconnected : args[0];
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            case "equals":
                                return proxy == args[0];
                            case "toString":
                                return "Waze phone connection transform";
                            default:
                                throw new UnsupportedOperationException(method.getName());
                        }
                    });
        }
    }
}
