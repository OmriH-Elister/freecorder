package com.omri.healthscanner;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity implements CameraScanner.Listener {
    private static final int CAMERA_PERMISSION = 41;
    private static final int NAVY = Color.rgb(7, 26, 51);
    private static final int NAVY_DARK = Color.rgb(5, 15, 31);
    private static final int CARD = Color.rgb(12, 39, 66);
    private static final int CARD_EDGE = Color.rgb(24, 77, 106);
    private static final int CYAN = Color.rgb(39, 181, 255);
    private static final int GREEN = Color.rgb(66, 232, 196);
    private static final int TEXT = Color.rgb(232, 245, 250);
    private static final int MUTED = Color.rgb(145, 174, 188);
    private static final int WARNING = Color.rgb(255, 196, 92);

    private TextureView textureView;
    private ScanOverlayView overlayView;
    private TextView scanStatus;
    private ProgressBar scanProgress;
    private Button scanButton;
    private EditText heightInput;
    private EditText weightInput;

    private TextView scoreValue;
    private TextView pulseValue;
    private TextView respirationValue;
    private TextView nutritionValue;
    private TextView summaryValue;
    private TextView anemiaValue;

    private CameraScanner cameraScanner;
    private Handler uiHandler;
    private boolean cameraReady;
    private boolean pendingScan;
    private boolean scanInProgress;
    private final Runnable scanTimeout = () -> {
        if (!scanInProgress && !pendingScan) return;
        scanInProgress = false;
        pendingScan = false;
        cameraReady = false;
        if (cameraScanner != null) {
            cameraScanner.close();
            cameraScanner = null;
        }
        scanButton.setEnabled(true);
        scanButton.setAlpha(1.0f);
        scanButton.setText("TRY CAMERA AGAIN");
        overlayView.setScanning(false);
        scanStatus.setText("Scan timed out because camera frames stopped arriving");
    };
    private SignalAnalyzer.Estimate pulse = SignalAnalyzer.Estimate.unavailable("Not scanned");
    private SignalAnalyzer.Estimate respiration = SignalAnalyzer.Estimate.unavailable("Not scanned");
    private NutritionAssessment.Result nutrition = NutritionAssessment.Result.unavailable("Not entered");

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY_DARK);
        getWindow().setNavigationBarColor(NAVY_DARK);
        setContentView(buildInterface());
        uiHandler = new Handler(getMainLooper());
        updateResults();
    }

    private View buildInterface() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(NAVY_DARK);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(32));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(com.omri.healthscanner.R.drawable.ic_launcher);
        header.addView(icon, new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(12), 0, 0, 0);
        TextView title = label("freecorder", 27, TEXT, true);
        TextView subtitle = label("CAMERA SIGNAL RESEARCH DEMO", 11, CYAN, true);
        subtitle.setLetterSpacing(0.14f);
        titles.addView(title);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(header);

        TextView badge = label("EXPERIMENTAL OFFLINE PROTOTYPE", 11, GREEN, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(12), dp(7), dp(12), dp(7));
        badge.setBackground(roundRect(Color.rgb(8, 55, 62), GREEN, 18, 1));
        LinearLayout.LayoutParams badgeParams = wrapParams();
        badgeParams.topMargin = dp(14);
        content.addView(badge, badgeParams);

        TextView safety = label(
                "Research prototype; not clinically validated or cleared for medical use. "
                        + "Do not use it for emergencies, diagnosis, or treatment decisions.",
                13, WARNING, false);
        safety.setPadding(dp(14), dp(12), dp(14), dp(12));
        safety.setBackground(roundRect(Color.rgb(61, 44, 20), WARNING, 14, 1));
        LinearLayout.LayoutParams safetyParams = matchWrapParams();
        safetyParams.topMargin = dp(12);
        content.addView(safety, safetyParams);

        content.addView(buildCameraCard(), spacedCardParams());
        content.addView(buildInputsCard(), spacedCardParams());
        content.addView(buildResultsCard(), spacedCardParams());

        TextView privacy = label(
                "PRIVACY  •  Frames are processed in memory on this phone. "
                        + "freecorder does not save video or use the internet.",
                11, MUTED, false);
        privacy.setGravity(Gravity.CENTER);
        privacy.setPadding(dp(8), dp(18), dp(8), 0);
        content.addView(privacy);
        return scroll;
    }

    private View buildCameraCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("01  CAMERA SIGNAL EXPERIMENT"));
        TextView instructions = label(
                "Sit still in even light, keep your face inside the frame, and breathe normally.",
                13, MUTED, false);
        instructions.setPadding(0, dp(4), 0, dp(12));
        card.addView(instructions);

        FrameLayout preview = new FrameLayout(this);
        preview.setBackgroundColor(Color.BLACK);
        textureView = new TextureView(this);
        preview.addView(textureView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlayView = new ScanOverlayView(this);
        preview.addView(overlayView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(250));
        card.addView(preview, previewParams);

        scanStatus = label(ScanStatusText.initial(), 13, TEXT, true);
        scanStatus.setPadding(0, dp(12), 0, dp(6));
        card.addView(scanStatus);
        scanProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        scanProgress.setMax(1000);
        scanProgress.setProgress(0);
        scanProgress.getProgressDrawable().setTint(GREEN);
        card.addView(scanProgress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(7)));

        scanButton = primaryButton("START 20-SECOND SCAN");
        LinearLayout.LayoutParams buttonParams = matchWrapParams();
        buttonParams.topMargin = dp(14);
        card.addView(scanButton, buttonParams);
        scanButton.setOnClickListener(view -> requestOrStartScan());
        return card;
    }

    private View buildInputsCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("02  ENTERED BODY DATA"));
        TextView explanation = label(
                "Appearance is not used to infer nutrition or body composition. BMI is shown only "
                        + "as arithmetic from entered height and weight; no risk classification or "
                        + "nutrition conclusion is made.",
                13, MUTED, false);
        explanation.setPadding(0, dp(4), 0, dp(12));
        card.addView(explanation);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        heightInput = numberInput("Height (cm)");
        weightInput = numberInput("Weight (kg)");
        row.addView(heightInput, weightedField(1f, 0, dp(6)));
        row.addView(weightInput, weightedField(1f, dp(6), 0));
        card.addView(row);


        Button assess = secondaryButton("UPDATE ENTERED-DATA RECAP");
        assess.setOnClickListener(view -> {
            nutrition = readNutritionInputs();
            updateResults();
        });
        LinearLayout.LayoutParams assessParams = matchWrapParams();
        assessParams.topMargin = dp(10);
        card.addView(assess, assessParams);
        return card;
    }

    private View buildResultsCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("03  EXPERIMENTAL OUTPUT"));

        LinearLayout scoreRow = new LinearLayout(this);
        scoreRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout scoreBox = new LinearLayout(this);
        scoreBox.setOrientation(LinearLayout.VERTICAL);
        scoreBox.setGravity(Gravity.CENTER);
        scoreBox.setPadding(dp(16), dp(12), dp(16), dp(12));
        scoreBox.setBackground(roundRect(Color.rgb(5, 29, 50), CYAN, 16, 1));
        scoreValue = label("N/A", 28, GREEN, true);
        scoreValue.setGravity(Gravity.CENTER);
        TextView scoreLabel = label("NO CLINICAL\nHEALTH SCORE", 9, MUTED, true);
        scoreLabel.setGravity(Gravity.CENTER);
        scoreBox.addView(scoreValue);
        scoreBox.addView(scoreLabel);
        scoreRow.addView(scoreBox, new LinearLayout.LayoutParams(dp(130), dp(116)));

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.VERTICAL);
        metrics.setPadding(dp(16), 0, 0, 0);
        pulseValue = metric("Pulse output", "Not displayed • unvalidated method");
        respirationValue = metric("Breathing output", "Not displayed • unvalidated method");
        nutritionValue = metric("Entered-data recap", "Not calculated");
        metrics.addView(pulseValue);
        metrics.addView(respirationValue);
        metrics.addView(nutritionValue);
        scoreRow.addView(metrics, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(scoreRow);

        summaryValue = label("Complete a camera scan and enter body measurements.", 14, TEXT, false);
        summaryValue.setPadding(0, dp(16), 0, dp(12));
        card.addView(summaryValue);

        anemiaValue = label(WellnessSummary.ANEMIA_NOTE, 12, WARNING, false);
        anemiaValue.setPadding(dp(12), dp(10), dp(12), dp(10));
        anemiaValue.setBackground(roundRect(Color.rgb(54, 39, 20), WARNING, 12, 1));
        card.addView(anemiaValue);

        TextView urgent = label(
                "For chest pain, severe breathlessness, fainting, confusion, blue lips, heavy "
                        + "bleeding, or rapidly worsening symptoms, call local emergency services "
                        + "immediately. Do not complete or repeat a scan first.",
                11, MUTED, false);
        urgent.setPadding(0, dp(14), 0, 0);
        card.addView(urgent);
        return card;
    }

    private void requestOrStartScan() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
            return;
        }
        prepareAndScan();
    }

    private void prepareAndScan() {
        scanButton.setEnabled(false);
        pendingScan = true;
        uiHandler.removeCallbacks(scanTimeout);
        uiHandler.postDelayed(scanTimeout, 15_000L);
        if (cameraScanner == null) {
            scanStatus.setText("Preparing front camera…");
            cameraScanner = new CameraScanner(this, textureView, this);
            cameraScanner.start();
        } else if (cameraReady) {
            beginScanNow();
        }
    }

    private void beginScanNow() {
        pendingScan = false;
        scanInProgress = true;
        uiHandler.removeCallbacks(scanTimeout);
        uiHandler.postDelayed(scanTimeout, 30_000L);
        scanProgress.setProgress(0);
        scanButton.setText("SCANNING…");
        scanButton.setAlpha(0.55f);
        overlayView.setScanning(true);
        scanStatus.setText("Scanning… keep still and breathe normally");
        cameraScanner.beginScan();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CAMERA_PERMISSION) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                prepareAndScan();
            } else {
                scanStatus.setText("Camera permission is required for the scan");
            }
        }
    }

    @Override public void onCameraReady() {
        cameraReady = true;
        if (pendingScan) beginScanNow();
    }

    @Override public void onProgress(double fraction, double signalValue) {
        scanProgress.setProgress((int) Math.round(fraction * 1000.0));
        int secondsLeft = Math.max(0, 20 - (int) Math.floor(fraction * 20.0));
        if (Double.isFinite(signalValue)) {
            scanStatus.setText(String.format(Locale.US,
                    "Scanning… %d seconds remaining", secondsLeft));
            overlayView.addSignal(signalValue);
        } else {
            scanStatus.setText(String.format(Locale.US,
                    "No face detected • %d seconds remaining", secondsLeft));
        }
    }

    @Override public void onComplete(SignalAnalyzer.Estimate pulseEstimate,
                                     SignalAnalyzer.Estimate respirationEstimate) {
        scanInProgress = false;
        uiHandler.removeCallbacks(scanTimeout);
        pulse = pulseEstimate;
        respiration = respirationEstimate;
        nutrition = readNutritionInputs();
        overlayView.setScanning(false);
        scanProgress.setProgress(1000);
        scanButton.setEnabled(true);
        scanButton.setAlpha(1.0f);
        scanButton.setText("SCAN AGAIN");
        scanStatus.setText(ScanStatusText.complete(pulse.available, respiration.available));
        updateResults();
    }

    @Override public void onError(String message) {
        scanInProgress = false;
        uiHandler.removeCallbacks(scanTimeout);
        pendingScan = false;
        cameraReady = false;
        if (cameraScanner != null) {
            cameraScanner.close();
            cameraScanner = null;
        }
        scanButton.setEnabled(true);
        scanButton.setAlpha(1.0f);
        scanButton.setText("TRY CAMERA AGAIN");
        overlayView.setScanning(false);
        scanStatus.setText(message);
    }

    private NutritionAssessment.Result readNutritionInputs() {
        try {
            double height = Double.parseDouble(heightInput.getText().toString().trim());
            double weight = Double.parseDouble(weightInput.getText().toString().trim());
            return NutritionAssessment.evaluate(height, weight, 0.0, false);
        } catch (NumberFormatException error) {
            return NutritionAssessment.Result.unavailable("Enter height and weight to calculate BMI");
        }
    }

    private void updateResults() {
        pulseValue.setText("Pulse output\nNot displayed • unvalidated method");
        respirationValue.setText("Breathing output\nNot displayed • unvalidated method");
        nutritionValue.setText(nutrition.available
                ? String.format(Locale.US, "Entered-data recap\nBMI %.1f • %s",
                    nutrition.bmi, nutrition.bodySizeLabel)
                : "Entered-data recap\n" + nutrition.reason);

        WellnessSummary.Result result = WellnessSummary.compose(pulse, respiration, nutrition);
        scoreValue.setText("N/A");
        summaryValue.setText(result.summary);
        anemiaValue.setText(result.anemiaNote);
    }

    @Override protected void onPause() {
        super.onPause();
        scanInProgress = false;
        if (uiHandler != null) uiHandler.removeCallbacks(scanTimeout);
        if (cameraScanner != null) {
            cameraScanner.close();
            cameraScanner = null;
            cameraReady = false;
            pendingScan = false;
            scanButton.setEnabled(true);
            scanButton.setAlpha(1.0f);
            scanButton.setText("START 20-SECOND SCAN");
            overlayView.setScanning(false);
        }
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(roundRect(CARD, CARD_EDGE, 18, 1));
        return card;
    }

    private TextView sectionTitle(String text) {
        TextView view = label(text, 13, CYAN, true);
        view.setLetterSpacing(0.1f);
        return view;
    }

    private TextView metric(String label, String value) {
        TextView view = label(label + "\n" + value, 12, TEXT, true);
        view.setPadding(0, dp(4), 0, dp(4));
        return view;
    }

    private EditText numberInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(MUTED);
        input.setTextColor(TEXT);
        input.setTextSize(14);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(roundRect(Color.rgb(7, 29, 50), CARD_EDGE, 12, 1));
        return input;
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(NAVY_DARK);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        button.setPadding(dp(12), dp(11), dp(12), dp(11));
        button.setBackground(roundRect(GREEN, GREEN, 13, 0));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(CYAN);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        button.setBackground(roundRect(Color.rgb(7, 29, 50), CYAN, 12, 1));
        return button;
    }

    private TextView label(String text, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(sizeSp);
        view.setLineSpacing(0f, 1.16f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable roundRect(int fill, int stroke, int radiusDp, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams spacedCardParams() {
        LinearLayout.LayoutParams params = matchWrapParams();
        params.topMargin = dp(16);
        return params;
    }

    private LinearLayout.LayoutParams weightedField(float weight, int left, int right) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        params.leftMargin = left;
        params.rightMargin = right;
        return params;
    }

    private LinearLayout.LayoutParams matchWrapParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class ScanOverlayView extends View {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint corner = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Float> trace = new ArrayList<>();
        private boolean scanning;

        ScanOverlayView(Activity context) {
            super(context);
            setWillNotDraw(false);
            corner.setColor(CYAN);
            corner.setStyle(Paint.Style.STROKE);
            corner.setStrokeWidth(4f);
            line.setColor(GREEN);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(3f);
        }

        void setScanning(boolean value) {
            scanning = value;
            if (!value) trace.clear();
            invalidate();
        }

        void addSignal(double value) {
            trace.add((float) value);
            if (trace.size() > 80) trace.remove(0);
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            RectF face = new RectF(w * 0.22f, h * 0.12f, w * 0.78f, h * 0.80f);
            float c = Math.min(w, h) * 0.10f;
            canvas.drawLine(face.left, face.top, face.left + c, face.top, corner);
            canvas.drawLine(face.left, face.top, face.left, face.top + c, corner);
            canvas.drawLine(face.right, face.top, face.right - c, face.top, corner);
            canvas.drawLine(face.right, face.top, face.right, face.top + c, corner);
            canvas.drawLine(face.left, face.bottom, face.left + c, face.bottom, corner);
            canvas.drawLine(face.left, face.bottom, face.left, face.bottom - c, corner);
            canvas.drawLine(face.right, face.bottom, face.right - c, face.bottom, corner);
            canvas.drawLine(face.right, face.bottom, face.right, face.bottom - c, corner);
            if (scanning && trace.size() > 2) {
                float min = Float.MAX_VALUE;
                float max = -Float.MAX_VALUE;
                for (float value : trace) { min = Math.min(min, value); max = Math.max(max, value); }
                float range = Math.max(0.01f, max - min);
                float previousX = 0f;
                float previousY = h * 0.90f;
                for (int i = 0; i < trace.size(); i++) {
                    float x = w * i / Math.max(1, trace.size() - 1);
                    float normalized = (trace.get(i) - min) / range;
                    float y = h * (0.96f - normalized * 0.12f);
                    if (i > 0) canvas.drawLine(previousX, previousY, x, y, line);
                    previousX = x;
                    previousY = y;
                }
            }
        }
    }
}
