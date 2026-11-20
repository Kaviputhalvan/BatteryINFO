package apk.battryinfo.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;

import java.util.List;
import java.util.Locale;

import apk.battryinfo.service.BatteryHistoryStore;

public final class TelemetryChartView extends View {

    public static final int SOC = 0;
    public static final int VOLTAGE = 1;
    public static final int CURRENT = 2;
    public static final int TEMPERATURE = 3;

    private final List<BatteryHistoryStore.Sample> samples;
    private final int mode;
    private final int color1;
    private final int color2;
    private final int textColor;
    private final int gridColor;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TelemetryChartView(
            Context context,
            List<BatteryHistoryStore.Sample> samples,
            int mode,
            int color1,
            int color2,
            int textColor,
            int gridColor) {
        super(context);
        this.samples = samples;
        this.mode = mode;
        this.color1 = color1;
        this.color2 = color2;
        this.textColor = textColor;
        this.gridColor = gridColor;
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    private int dp(float value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();

        if (width <= 0 || height <= 0) return;

        String title = mode == SOC ? "State of charge"
                : mode == VOLTAGE ? "Pack voltage"
                : mode == CURRENT ? "Pack current"
                : "Temperature";

        String unit = mode == SOC ? "%"
                : mode == VOLTAGE ? "V"
                : mode == CURRENT ? "A" : "°C";

        paint.setColor(textColor);
        paint.setTextSize(dp(14));
        paint.setTypeface(Typeface.create(
                Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(title, dp(4), dp(20), paint);

        if (samples.size() < 2) {
            paint.setTextSize(dp(12));
            paint.setTypeface(Typeface.DEFAULT);
            canvas.drawText("Not enough samples for a trend",
                    dp(4), height / 2, paint);
            return;
        }

        float left = dp(43);
        float right = width - dp(12);
        float top = dp(42);
        float bottom = height - dp(28);

        if (right <= left || bottom <= top) return;

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (BatteryHistoryStore.Sample sample : samples) {
            double[] values = values(sample);

            for (double value : values) {
                if (!Double.isNaN(value) && !Double.isInfinite(value)) {
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                }
            }
        }

        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            paint.setColor(textColor);
            paint.setTextSize(dp(12));
            canvas.drawText("No valid measurements",
                    left, (top + bottom) / 2, paint);
            return;
        }

        if (mode == CURRENT) {
            min = Math.min(min, 0);
            max = Math.max(max, 0);
        }

        if (Math.abs(max - min) < 0.01) {
            double padding = Math.max(0.5, Math.abs(max) * 0.05);
            min -= padding;
            max += padding;
        } else {
            double padding = (max - min) * 0.12;
            min -= padding;
            max += padding;
        }

        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(dp(10));
        paint.setStrokeWidth(dp(1));
        paint.setColor(gridColor);

        for (int i = 0; i <= 4; i++) {
            float y = top + (bottom - top) * i / 4f;
            canvas.drawLine(left, y, right, y, paint);

            double value = max - (max - min) * i / 4.0;
            paint.setColor(textColor);
            canvas.drawText(
                    String.format(Locale.getDefault(), "%.1f", value),
                    dp(2), y + dp(4), paint);
            paint.setColor(gridColor);
        }

        drawSeries(canvas, left, top, right, bottom,
                min, max, 0, color1);

        if (mode == SOC || mode == TEMPERATURE) {
            drawSeries(canvas, left, top, right, bottom,
                    min, max, 1, color2);
        }

        paint.setColor(textColor);
        paint.setTextSize(dp(10));

        canvas.drawText("Oldest", left, height - dp(7), paint);

        String latest = "Latest";
        canvas.drawText(latest,
                right - paint.measureText(latest),
                height - dp(7), paint);

        paint.setTextSize(dp(9));
        canvas.drawText(unit, dp(4), top - dp(8), paint);

        if (mode == SOC || mode == TEMPERATURE) {
            float legendY = height - dp(7);
            float legendX = width / 2 - dp(38);

            paint.setColor(color1);
            canvas.drawCircle(legendX, legendY - dp(3), dp(3), paint);

            paint.setColor(textColor);
            canvas.drawText(
                    mode == SOC ? "BMS" : "Cell",
                    legendX + dp(7), legendY, paint);

            legendX += dp(48);
            paint.setColor(color2);
            canvas.drawCircle(legendX, legendY - dp(3), dp(3), paint);

            paint.setColor(textColor);
            canvas.drawText(
                    mode == SOC ? "Est." : "MOS",
                    legendX + dp(7), legendY, paint);
        }
    }

    private double[] values(BatteryHistoryStore.Sample sample) {
        switch (mode) {
            case SOC:
                return new double[]{
                        sample.hasReportedSoc
                                ? sample.reportedSoc : Double.NaN,
                        sample.hasEstimatedSoc
                                ? sample.estimatedSoc : Double.NaN
                };

            case VOLTAGE:
                return new double[]{sample.voltage, Double.NaN};

            case CURRENT:
                return new double[]{sample.current, Double.NaN};

            case TEMPERATURE:
                return new double[]{
                        sample.hasMaximumTemperature
                                ? sample.maximumTemperature : Double.NaN,
                        sample.hasMosTemperature
                                ? sample.mosTemperature : Double.NaN
                };

            default:
                return new double[]{Double.NaN, Double.NaN};
        }
    }

    private void drawSeries(
            Canvas canvas,
            float left,
            float top,
            float right,
            float bottom,
            double min,
            double max,
            int series,
            int color) {

        Path path = new Path();
        boolean started = false;

        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        for (int i = 0; i < samples.size(); i++) {
            double value = values(samples.get(i))[series];

            if (Double.isNaN(value) || Double.isInfinite(value)) {
                started = false;
                continue;
            }

            float x = left + (right - left) *
                    i / Math.max(1f, samples.size() - 1f);

            float y = bottom - (float) (
                    (value - min) / (max - min) *
                    (bottom - top));

            if (!started) {
                path.moveTo(x, y);
                started = true;
            } else {
                path.lineTo(x, y);
            }
        }

        canvas.drawPath(path, paint);

        paint.setStyle(Paint.Style.FILL);
    }
}
