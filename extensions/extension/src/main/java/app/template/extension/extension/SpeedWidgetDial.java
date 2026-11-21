package app.template.extension.extension;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

final class SpeedWidgetDial {
    private static final int MINT = 0xff70edda;
    private static final int AMBER = 0xffffb759;
    private static final Typeface SPEED = Typeface.create("sans-serif-medium", Typeface.BOLD);
    private static final Typeface LABEL = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private SpeedWidgetDial() {}

    static Bitmap draw(String speed, String units, String limit, String hint) {
        int current = number(speed);
        int posted = number(limit);
        boolean over = posted > 0 && current > posted;
        int accent = over ? AMBER : MINT;

        Bitmap result = Bitmap.createBitmap(352, 352, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint brush = new Paint(Paint.ANTI_ALIAS_FLAG);
        float center = 176;
        float centerY = 185;
        circle(canvas, brush, center, centerY, 142, 0xff0b1422);
        circle(canvas, brush, center, centerY, 137, 0xff192939);
        circle(canvas, brush, center, centerY, 122, 0xff101c2b);

        RectF ring = new RectF(center - 128, centerY - 128, center + 128, centerY + 128);
        brush.setStyle(Paint.Style.STROKE);
        brush.setStrokeWidth(7);
        brush.setStrokeCap(Paint.Cap.ROUND);
        final int steps = 31;
        int range = "mph".equals(units) ? 120 : 180;
        int active = current < 0 ? -1 : Math.min(steps, Math.round((float) current / range * steps));
        for (int tick = 0; tick <= steps; tick++) {
            brush.setColor(tick <= active ? accent : 0xff435b6f);
            canvas.drawArc(ring, 142 + tick * (256f / steps), 3.5f, false, brush);
        }
        brush.setStyle(Paint.Style.FILL);

        text(canvas, brush, speed, center, centerY - 7, current > 199 ? 82 : 94, over ? AMBER : 0xffecf8fb, SPEED);
        text(canvas, brush, units, center, centerY + 58, 28, 0xffabbfce, LABEL);
        if (!hint.isEmpty()) {
            text(canvas, brush, hint, center, centerY + 100, 17, 0xffabbfce, LABEL);
        }
        if (posted > 0) {
            circle(canvas, brush, 276, 85, 68, 0xff0b1422);
            circle(canvas, brush, 276, 85, 61, 0xffeb624f);
            circle(canvas, brush, 276, 85, 49, Color.WHITE);
            text(canvas, brush, limit, 276, 85, limit.length() > 2 ? 40 : 52, 0xff111c29, SPEED);
        }
        return result;
    }

    private static int number(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static void circle(Canvas canvas, Paint brush, float x, float y, float radius, int color) {
        brush.setColor(color);
        canvas.drawCircle(x, y, radius, brush);
    }

    private static void text(Canvas canvas, Paint brush, String value, float x, float y,
                             float size, int color, Typeface typeface) {
        brush.setTypeface(typeface);
        brush.setTextSize(size);
        brush.setTextAlign(Paint.Align.CENTER);
        brush.setColor(color);
        canvas.drawText(value, x, y - (brush.ascent() + brush.descent()) / 2, brush);
    }
}
