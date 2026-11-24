package app.template.extension.extension;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextUtils;

import java.util.List;

final class NavigationWidgetPanel {
    private static final int INK = 0xffffffff;
    private static final int MUTED = 0xffaeb4ba;
    private static final int ACCENT = 0xff46c9ee;
    private static final int WIDTH = 700;
    private static final int HEIGHT = 170;
    private static final int GUIDANCE_WIDTH = 280;
    private static final int SPEED_WIDTH = 270;

    private final Canvas canvas;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.text.TextPaint measure = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);

    private NavigationWidgetPanel(Canvas canvas) { this.canvas = canvas; }

    static Bitmap draw(int turn, int exit, String distance, String street,
                       String speed, String units, String limit, boolean usSign,
                       List<NavigationWidget.Alert> alerts) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        NavigationWidgetPanel panel = new NavigationWidgetPanel(canvas);
        int guidanceWidth = GUIDANCE_WIDTH;
        int speedEnd = guidanceWidth + SPEED_WIDTH;
        // The transparent inset prevents launcher clipping from flattening the corners.
        panel.outlinedBox(5, 5, WIDTH - 5, HEIGHT - 5, 46, 0xbf0e0e14, 0xff364258);
        panel.box(guidanceWidth - 1, 47, guidanceWidth + 1, 123, 0, 0x80ffffff);
        panel.box(speedEnd - 1, 47, speedEnd + 1, 123, 0, 0x80ffffff);
        panel.maneuver(turn, exit, distance, street);
        panel.text(speed, guidanceWidth + 66, 89, speed.length() > 2 ? 50 : 58, INK, true);
        panel.text(units, guidanceWidth + 66, 124, 20, MUTED, false);
        panel.speedSign(guidanceWidth + 185, 85, 43, limit, usSign);

        if (!alerts.isEmpty()) {
            for (int index = 0; index < alerts.size(); index++) {
                NavigationWidget.Alert alert = alerts.get(index);
                float cellWidth = 135f / alerts.size();
                panel.alert(alert, speedEnd + 5 + cellWidth * (index + .5f), units,
                        Math.min(1f, cellWidth / 55f));
            }
        }
        return bitmap;
    }

    private void maneuver(int turn, int exit, String distance, String street) {
        if (turn <= 0) {
            fitText(street, 140, 94, 27, 220, INK, true);
            return;
        }
        String arrow;
        switch (direction(turn)) {
            case "Turn left": arrow = "↰"; break;
            case "Turn right": arrow = "↱"; break;
            case "Keep left": arrow = "↖"; break;
            case "Keep right": arrow = "↗"; break;
            case "U-turn": arrow = "↶"; break;
            case "Roundabout": arrow = "⟳"; break;
            case "Destination": arrow = "⚑"; break;
            default: arrow = "↑"; break;
        }
        text(arrow, 58, 100, 65, INK, true);
        if (exit > 0) {
            box(35, 96, 72, 124, 8, 0xff16283a);
            text(Integer.toString(exit), 54, 117, 20, ACCENT, true);
        }
        fitText(distance.isEmpty() ? direction(turn) : distance, 171, street.isEmpty() ? 94 : 78, 29, 160, INK, true);
        if (!street.isEmpty()) fitText(street, 171, 113, 20, 160, ACCENT, false);
    }

    static String direction(int turn) {
        switch (turn) {
            case 1: case 23: case 27: return "Turn left";
            case 2: case 25: case 28: return "Turn right";
            case 3: case 17: case 22: case 29: return "Keep left";
            case 4: case 18: case 24: case 30: return "Keep right";
            case 16: case 31: return "Destination";
            case 20: return "U-turn";
            default: return turn >= 6 && turn <= 15 ? "Roundabout" : "Continue straight";
        }
    }

    private void alert(NavigationWidget.Alert alert, float x, String units, float scale) {
        canvas.save();
        canvas.scale(scale, scale, x, 85);
        if (alert.limit > 0) {
            speedSign(x, 63, 21, Integer.toString(alert.limit), false);
        } else {
            circle(x, 63, 22, 0xff2f4d64);
            if (alert.type == 10) {
                box(x - 14, 55, x + 14, 72, 4, ACCENT);
                box(x - 7, 51, x + 6, 58, 2, ACCENT);
                circle(x, 63, 5, 0xff2f4d64);
            } else {
                String symbol;
                switch (alert.type) {
                    case 1: symbol = "P"; break;
                    case 3: symbol = "≋"; break;
                    case 12: symbol = "×"; break;
                    default: symbol = "!"; break;
                }
                text(symbol, x, 71, 25, ACCENT, true);
            }
        }
        text(NavigationWidget.formatDistance(alert.meters, units), x, 113, 17, INK, true);
        canvas.restore();
    }

    private void speedSign(float x, float y, float radius, String value, boolean usSign) {
        if (usSign) {
            box(x - radius * .77f, y - radius, x + radius * .77f, y + radius, 6, Color.WHITE);
            box(x - radius * .69f, y - radius * .92f, x + radius * .69f, y + radius * .92f, 4, 0xff172838);
            box(x - radius * .61f, y - radius * .84f, x + radius * .61f, y + radius * .84f, 3, Color.WHITE);
            text("LIMIT", x, y - radius * .3f, radius * .29f, 0xff162537, true);
            text(value, x, y + radius * .53f, radius * .76f, 0xff162537, true);
        } else {
            boolean unknown = value.equals("—") || value.equals("-") || value.isEmpty();
            circle(x, y, radius, unknown ? 0xffb0b0b0 : 0xffff0000);
            circle(x, y, radius * .89f, unknown ? 0xffededed : Color.WHITE);
            text(value, x, y + radius * .33f, radius * (value.length() > 2 ? .86f : 1.03f),
                    unknown ? 0xff6e7479 : Color.BLACK, true);
        }
    }

    private void fitText(String value, float x, float y, int size, int width, int color, boolean bold) {
        measure.setTextSize(size);
        measure.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        text(TextUtils.ellipsize(value, measure, width, TextUtils.TruncateAt.END).toString(),
                x, y, size, color, bold);
    }

    private void text(String value, float x, float y, float size, int color, boolean bold) {
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextSize(size);
        paint.setColor(color);
        canvas.drawText(value, x, y, paint);
    }

    private void circle(float x, float y, float radius, int color) {
        paint.setColor(color);
        canvas.drawCircle(x, y, radius, paint);
    }

    private void box(float left, float top, float right, float bottom, float radius, int color) {
        paint.setColor(color);
        canvas.drawRoundRect(new RectF(left, top, right, bottom), radius, radius, paint);
    }

    private void outlinedBox(float left, float top, float right, float bottom, float radius,
                             int fill, int stroke) {
        paint.setStyle(Paint.Style.FILL);
        box(left, top, right, bottom, radius, fill);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        box(left + 1, top + 1, right - 1, bottom - 1, radius - 1, stroke);
        paint.setStyle(Paint.Style.FILL);
    }
}
