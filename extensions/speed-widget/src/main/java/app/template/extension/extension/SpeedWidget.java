package app.template.extension.extension;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.widget.RemoteViews;

public final class SpeedWidget extends AppWidgetProvider {
    static ComponentName component(Context context) {
        return new ComponentName(context, SpeedWidget.class);
    }

    static boolean isBound(Context context, int id) {
        for (int bound : AppWidgetManager.getInstance(context).getAppWidgetIds(component(context))) {
            if (bound == id) return true;
        }
        return false;
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        SpeedWidgetAuto.observe(context);
        SpeedWidgetService.refresh(context);
    }

    @Override
    public void onDeleted(Context context, int[] widgetIds) {
        for (int id : widgetIds) SpeedWidgetService.widgetDeleted(id);
    }

    @Override
    public void onDisabled(Context context) {
        SpeedWidgetAuto.removeWatchers(context);
        context.stopService(new Intent(context, SpeedWidgetService.class));
    }

    static void render(Context context, String speed, String unit, String limit, String status) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] widgetIds = manager.getAppWidgetIds(component(context));
        if (widgetIds.length == 0) return;
        Bitmap dial = SpeedWidgetDial.draw(speed, unit, limit, status);
        int layout = resource(context, "layout", "qm_speed_widget");
        int container = resource(context, "id", "qm_widget_container");
        String speedDescription = "—".equals(speed) ? "Speed unavailable" : "Speed " + speed + " " + unit;
        String limitDescription = "—".equals(limit) ? "Waze speed limit unavailable" : "Waze speed limit " + limit + " " + unit;
        for (int id : widgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), layout);
            views.setImageViewBitmap(container, dial);
            views.setContentDescription(container, speedDescription + ". " + limitDescription + ". " + status);
            Intent controls = new Intent(context, SpeedWidgetControls.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            views.setOnClickPendingIntent(container, PendingIntent.getActivity(context, id, controls, PendingIntent.FLAG_IMMUTABLE));
            manager.updateAppWidget(id, views);
        }
    }

    private static int resource(Context context, String type, String name) {
        return context.getResources().getIdentifier(name, type, context.getPackageName());
    }
}
