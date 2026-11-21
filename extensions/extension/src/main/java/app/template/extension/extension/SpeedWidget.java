package app.template.extension.extension;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public final class SpeedWidget extends AppWidgetProvider {
    static ComponentName component(Context context) {
        return new ComponentName(context, SpeedWidget.class);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        SpeedWidgetService.refresh(context);
    }

    @Override
    public void onDisabled(Context context) {
        context.stopService(new Intent(context, SpeedWidgetService.class));
    }

    static void render(Context context, String speed, String unit, String limit, String status) {
        RemoteViews views = new RemoteViews(context.getPackageName(), resource(context, "layout", "qm_speed_widget"));
        int container = resource(context, "id", "qm_widget_container");
        views.setTextViewText(resource(context, "id", "qm_widget_speed"), speed);
        views.setTextViewText(resource(context, "id", "qm_widget_unit"), unit);
        views.setTextViewText(resource(context, "id", "qm_widget_limit"), limit);
        views.setTextViewText(resource(context, "id", "qm_widget_status"), status);
        views.setContentDescription(container, "Speed " + speed + " " + unit + ". Waze speed limit " + limit + ". " + status);
        Intent controls = new Intent(context, SpeedWidgetControls.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        views.setOnClickPendingIntent(container, PendingIntent.getActivity(context, 0, controls, PendingIntent.FLAG_IMMUTABLE));
        AppWidgetManager.getInstance(context).updateAppWidget(component(context), views);
    }

    private static int resource(Context context, String type, String name) {
        return context.getResources().getIdentifier(name, type, context.getPackageName());
    }
}
