package com.omri.healthscanner;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Camera2 preview and in-memory colour trace capture. No video or image is saved. */
public final class CameraScanner {
    public interface Listener {
        void onCameraReady();
        void onProgress(double fraction, double signalValue);
        void onComplete(SignalAnalyzer.Estimate pulse, SignalAnalyzer.Estimate respiration);
        void onError(String message);
    }

    private static final double SCAN_SECONDS = 20.0;
    private final Context context;
    private final TextureView textureView;
    private final Listener listener;
    private final Handler mainHandler;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession session;
    private ImageReader imageReader;
    private Surface previewSurface;
    private final List<SignalAnalyzer.Sample> samples = new ArrayList<>();
    private final AtomicBoolean scanning = new AtomicBoolean(false);
    private final AtomicBoolean faceDetected = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(true);
    private boolean faceDetectionSupported;
    private volatile Rect detectedFaceBounds;
    private volatile Rect currentCropRegion;
    private Rect sensorActiveArray;
    private int totalScanFrames;
    private int faceScanFrames;
    private double currentFaceStartSeconds = Double.NaN;
    private double maxContinuousFaceSeconds;
    private long scanStartNanos;

    public CameraScanner(Context context, TextureView textureView, Listener listener) {
        this.context = context;
        this.textureView = textureView;
        this.listener = listener;
        this.mainHandler = new Handler(context.getMainLooper());
    }

    public void start() {
        if (!closed.compareAndSet(true, false)) return;
        cameraThread = new HandlerThread("FreecorderCamera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    if (!closed.get()) openCamera();
                }
                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { return true; }
                @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
            });
        }
    }

    public void beginScan() {
        synchronized (samples) {
            samples.clear();
        }
        totalScanFrames = 0;
        faceScanFrames = 0;
        currentFaceStartSeconds = Double.NaN;
        maxContinuousFaceSeconds = 0.0;
        faceDetected.set(false);
        detectedFaceBounds = null;
        currentCropRegion = null;
        scanStartNanos = SystemClock.elapsedRealtimeNanos();
        scanning.set(true);
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        scanning.set(false);
        faceDetected.set(false);
        detectedFaceBounds = null;
        currentCropRegion = null;
        textureView.setSurfaceTextureListener(null);
        Handler handler = cameraHandler;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (session != null) {
            session.close();
            session = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.setOnImageAvailableListener(null, null);
            imageReader.close();
            imageReader = null;
        }
        if (previewSurface != null) {
            previewSurface.release();
            previewSurface = null;
        }
        HandlerThread thread = cameraThread;
        cameraThread = null;
        cameraHandler = null;
        if (thread != null) {
            thread.quitSafely();
            try {
                thread.join(500L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void openCamera() {
        if (closed.get()) return;
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            String cameraId = findFrontCamera(manager);
            if (cameraId == null) {
                postError("No front camera was found");
                return;
            }
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            sensorActiveArray = characteristics.get(
                    CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            Integer maxFaces = characteristics.get(
                    CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT);
            int[] faceModes = characteristics.get(
                    CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
            faceDetectionSupported = maxFaces != null && maxFaces > 0
                    && containsFaceMode(faceModes, CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_SIMPLE);
            if (!faceDetectionSupported || sensorActiveArray == null) {
                postError("This front camera cannot provide the required face-detection data");
                return;
            }
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                postError("Camera stream configuration is unavailable");
                return;
            }
            Size analysisSize = chooseAnalysisSize(map.getOutputSizes(ImageFormat.YUV_420_888));
            imageReader = ImageReader.newInstance(analysisSize.getWidth(), analysisSize.getHeight(),
                    ImageFormat.YUV_420_888, 3);
            imageReader.setOnImageAvailableListener(this::onImageAvailable, cameraHandler);
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice camera) {
                    if (closed.get()) {
                        camera.close();
                        return;
                    }
                    cameraDevice = camera;
                    createSession(analysisSize);
                }
                @Override public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    if (!closed.get()) postError("Camera disconnected");
                }
                @Override public void onError(CameraDevice camera, int error) {
                    camera.close();
                    if (!closed.get()) postError("Camera error " + error);
                }
            }, cameraHandler);
        } catch (CameraAccessException | SecurityException error) {
            postError("Unable to open camera: " + error.getMessage());
        }
    }

    private String findFrontCamera(CameraManager manager) throws CameraAccessException {
        for (String id : manager.getCameraIdList()) {
            Integer facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) return id;
        }
        return null;
    }

    private boolean containsFaceMode(int[] modes, int wanted) {
        if (modes == null) return false;
        for (int mode : modes) {
            if (mode == wanted) return true;
        }
        return false;
    }

    private Face largestFace(Face[] faces) {
        if (faces == null || faces.length == 0) return null;
        Face largest = null;
        long largestArea = -1L;
        for (Face face : faces) {
            Rect bounds = face.getBounds();
            long area = (long) bounds.width() * bounds.height();
            if (area > largestArea) {
                largestArea = area;
                largest = face;
            }
        }
        return largest;
    }

    private Size chooseAnalysisSize(Size[] sizes) {
        if (sizes == null || sizes.length == 0) return new Size(640, 480);
        return Arrays.stream(sizes)
                .filter(size -> size.getWidth() <= 640 && size.getHeight() <= 480)
                .max(Comparator.comparingInt(size -> size.getWidth() * size.getHeight()))
                .orElse(Arrays.stream(sizes)
                        .min(Comparator.comparingInt(size -> size.getWidth() * size.getHeight()))
                        .orElse(sizes[0]));
    }

    private void createSession(Size size) {
        if (closed.get()) return;
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture == null || cameraDevice == null || imageReader == null) {
                postError("Camera preview surface is unavailable");
                return;
            }
            texture.setDefaultBufferSize(size.getWidth(), size.getHeight());
            previewSurface = new Surface(texture);
            List<Surface> outputs = Arrays.asList(previewSurface, imageReader.getSurface());
            cameraDevice.createCaptureSession(outputs, new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession configured) {
                    if (closed.get() || cameraDevice == null || imageReader == null
                            || previewSurface == null) {
                        configured.close();
                        return;
                    }
                    session = configured;
                    try {
                        CaptureRequest.Builder request = cameraDevice.createCaptureRequest(
                                CameraDevice.TEMPLATE_PREVIEW);
                        request.addTarget(previewSurface);
                        request.addTarget(imageReader.getSurface());
                        request.set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                        request.set(CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AE_MODE_ON);
                        if (faceDetectionSupported) {
                            request.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                                    CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_SIMPLE);
                        }
                        session.setRepeatingRequest(request.build(),
                                new CameraCaptureSession.CaptureCallback() {
                                    @Override public void onCaptureCompleted(
                                            CameraCaptureSession captureSession,
                                            CaptureRequest captureRequest,
                                            TotalCaptureResult result) {
                                        if (closed.get()) return;
                                        Face[] faces = result.get(
                                                android.hardware.camera2.CaptureResult.STATISTICS_FACES);
                                        Face largest = largestFace(faces);
                                        if (largest == null) {
                                            faceDetected.set(false);
                                            detectedFaceBounds = null;
                                        } else {
                                            faceDetected.set(true);
                                            detectedFaceBounds = new Rect(largest.getBounds());
                                        }
                                        Rect crop = result.get(android.hardware.camera2.CaptureResult.SCALER_CROP_REGION);
                                        currentCropRegion = crop == null ? null : new Rect(crop);
                                    }
                                }, cameraHandler);
                        mainHandler.post(() -> {
                            if (!closed.get()) listener.onCameraReady();
                        });
                    } catch (CameraAccessException error) {
                        postError("Unable to start camera preview: " + error.getMessage());
                    }
                }
                @Override public void onConfigureFailed(CameraCaptureSession failed) {
                    failed.close();
                    if (!closed.get()) postError("Camera preview configuration failed");
                }
            }, cameraHandler);
        } catch (CameraAccessException error) {
            postError("Unable to configure camera: " + error.getMessage());
        }
    }

    private void onImageAvailable(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null || closed.get() || !scanning.get()) return;
            double elapsed = (SystemClock.elapsedRealtimeNanos() - scanStartNanos) / 1_000_000_000.0;
            if (elapsed >= SCAN_SECONDS) {
                scanning.set(false);
                finishScan();
                return;
            }
            totalScanFrames++;
            if (!faceDetectionSupported || !faceDetected.get() || detectedFaceBounds == null) {
                if (!Double.isNaN(currentFaceStartSeconds)) {
                    maxContinuousFaceSeconds = Math.max(maxContinuousFaceSeconds,
                            elapsed - currentFaceStartSeconds);
                    currentFaceStartSeconds = Double.NaN;
                }
                double progress = elapsed / SCAN_SECONDS;
                mainHandler.post(() -> {
                    if (!closed.get()) listener.onProgress(progress, Double.NaN);
                });
                return;
            }
            faceScanFrames++;
            if (Double.isNaN(currentFaceStartSeconds)) currentFaceStartSeconds = elapsed;
            maxContinuousFaceSeconds = Math.max(maxContinuousFaceSeconds,
                    elapsed - currentFaceStartSeconds);
            double green = averageGreen(image, detectedFaceBounds, currentCropRegion);
            if (Double.isFinite(green)) {
                synchronized (samples) {
                    samples.add(new SignalAnalyzer.Sample(elapsed, green));
                }
                double progress = elapsed / SCAN_SECONDS;
                mainHandler.post(() -> {
                    if (!closed.get()) listener.onProgress(progress, green);
                });
            }
        } catch (RuntimeException error) {
            scanning.set(false);
            postError("Camera frame analysis failed");
        } finally {
            if (image != null) image.close();
        }
    }

    private double averageGreen(Image image, Rect faceBounds, Rect cropRegion) {
        Image.Plane[] planes = image.getPlanes();
        if (planes.length < 3 || faceBounds == null || sensorActiveArray == null) {
            return Double.NaN;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        Rect crop = cropRegion == null ? sensorActiveArray : cropRegion;
        int[] mapped = FrameRegionMapper.map(
                sensorActiveArray.left, sensorActiveArray.top,
                sensorActiveArray.right, sensorActiveArray.bottom,
                crop.left, crop.top, crop.right, crop.bottom,
                faceBounds.left, faceBounds.top, faceBounds.right, faceBounds.bottom,
                width, height);
        if (mapped == null) return Double.NaN;
        int faceLeft = mapped[0];
        int faceTop = mapped[1];
        int faceRight = mapped[2];
        int faceBottom = mapped[3];
        int faceWidth = faceRight - faceLeft;
        int faceHeight = faceBottom - faceTop;
        int left = faceLeft + faceWidth * 20 / 100;
        int right = faceRight - faceWidth * 20 / 100;
        int top = faceTop + faceHeight * 15 / 100;
        int bottom = faceTop + faceHeight * 48 / 100;
        if (right - left < 8 || bottom - top < 8) return Double.NaN;
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();
        int yRow = planes[0].getRowStride();
        int yPixel = planes[0].getPixelStride();
        int uRow = planes[1].getRowStride();
        int uPixel = planes[1].getPixelStride();
        int vRow = planes[2].getRowStride();
        int vPixel = planes[2].getPixelStride();
        double total = 0.0;
        int count = 0;
        for (int y = top; y < bottom; y += 4) {
            for (int x = left; x < right; x += 4) {
                int yIndex = y * yRow + x * yPixel;
                int uvX = x / 2;
                int uvY = y / 2;
                int uIndex = uvY * uRow + uvX * uPixel;
                int vIndex = uvY * vRow + uvX * vPixel;
                if (yIndex >= yBuffer.limit() || uIndex >= uBuffer.limit() || vIndex >= vBuffer.limit()) {
                    continue;
                }
                int yy = yBuffer.get(yIndex) & 0xFF;
                int uu = (uBuffer.get(uIndex) & 0xFF) - 128;
                int vv = (vBuffer.get(vIndex) & 0xFF) - 128;
                double green = yy - 0.344136 * uu - 0.714136 * vv;
                total += green;
                count++;
            }
        }
        return count == 0 ? Double.NaN : total / count;
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private void finishScan() {
        if (!Double.isNaN(currentFaceStartSeconds)) {
            double elapsed = (SystemClock.elapsedRealtimeNanos() - scanStartNanos)
                    / 1_000_000_000.0;
            maxContinuousFaceSeconds = Math.max(maxContinuousFaceSeconds,
                    elapsed - currentFaceStartSeconds);
        }
        if (!faceDetectionSupported
                || !ScanQualityGate.accept(totalScanFrames, faceScanFrames,
                    maxContinuousFaceSeconds)) {
            SignalAnalyzer.Estimate unavailable = SignalAnalyzer.Estimate.unavailable(
                    "Keep one face centered and continuously visible for most of the scan");
            mainHandler.post(() -> {
                if (!closed.get()) listener.onComplete(unavailable, unavailable);
            });
            return;
        }
        List<SignalAnalyzer.Sample> copy;
        synchronized (samples) {
            copy = new ArrayList<>(samples);
        }
        SignalAnalyzer.Estimate pulse = SignalAnalyzer.estimate(copy, 0.75, 3.0);
        SignalAnalyzer.Estimate respiration = SignalAnalyzer.estimate(copy, 0.10, 0.50);
        mainHandler.post(() -> {
            if (!closed.get()) listener.onComplete(pulse, respiration);
        });
    }

    private void postError(String message) {
        if (closed.get()) return;
        mainHandler.post(() -> {
            if (!closed.get()) listener.onError(message);
        });
    }
}
